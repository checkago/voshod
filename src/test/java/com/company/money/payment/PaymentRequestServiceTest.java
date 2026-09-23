package com.company.money.payment;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.Nomenclature;
import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.PaymentRequestNotice;
import com.company.money.entity.PaymentRequestStatus;
import com.company.money.entity.User;
import com.company.money.onec.OneCOdataClient;
import com.company.money.service.OneCExportMode;
import com.company.money.service.OneCReturnAction;
import com.company.money.service.PaymentRequestException;
import com.company.money.service.PaymentRequestService;
import com.company.money.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class PaymentRequestServiceTest {

    @Autowired
    DataManager dataManager;
    @Autowired
    PaymentRequestService paymentRequestService;
    @Autowired
    FileStorageLocator fileStorageLocator;
    @MockitoBean
    OneCOdataClient oneCOdataClient;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void submitsApprovesAndExportsPaymentOrderPayload() {
        Counterparty counterparty = counterparty("ООО Тест");
        Nomenclature nomenclature = nomenclature("Услуга");
        PaymentRequest request = paymentRequest(counterparty, nomenclature, "100.50", "Оплата по счёту");
        attachInvoice(request);

        PaymentRequest submitted = paymentRequestService.submit(request.getId());
        assertThat(submitted.getStatus()).isEqualTo(PaymentRequestStatus.SUBMITTED);
        assertThat(submitted.getRequestNumber()).startsWith("ЗОП-");
        assertThat(paymentRequestService.loadForWorkflow(request.getId()).getLastChangedBy()).isNotNull();

        LocalDate paymentDate = LocalDate.now().plusDays(1);
        PaymentRequest approved = paymentRequestService.approve(request.getId(), paymentDate, "Ок");
        assertThat(approved.getStatus()).isEqualTo(PaymentRequestStatus.APPROVED);
        assertThat(approved.getApprovedPaymentDate()).isEqualTo(paymentDate);

        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        stubBinaryStorage();

        PaymentRequest exported = paymentRequestService.exportToOneC(request.getId());
        assertThat(exported.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        assertThat(exported.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(exported.getOneCDocumentNumber()).isEqualTo("00БП-000001");
        assertThat(exported.getOneCDocumentTitle()).contains("ПП №00БП-000001");
        assertThat(exported.getOneCExportPayload())
                .contains("Document_ПлатежноеПоручение")
                .contains("ОплатаПоставщику")
                .contains("\"Posted\":false")
                .contains(counterparty.getOneCRef().toString())
                .contains("100.50")
                .contains("00БП-000001")
                .contains("СчетКонтрагента");
        verify(oneCOdataClient).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("\"Posted\":true"));
        verify(oneCOdataClient).patchRaw(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(),
                contains("Согласовано"));
    }

    @Test
    void exportKeepsSentAndDoesNotMarkDeletedWhenAttachFails() {
        PaymentRequest request = paymentRequest(counterparty("Файл 1С"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001","Date":"2026-09-22T00:00:00"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenThrow(new PaymentRequestException("attach 400"));

        assertThatThrownBy(() -> paymentRequestService.exportToOneC(request.getId()))
                .isInstanceOf(PaymentRequestException.class);
        PaymentRequest persisted = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(persisted.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        assertThat(persisted.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        verify(oneCOdataClient, never()).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("\"DeletionMark\":true"));
    }

    @Test
    void returnFromOneCUsesPaymentOrderStateNotPortalStatus() {
        PaymentRequest request = paymentRequest(counterparty("Возврат APPROVED"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        PaymentRequest approved = paymentRequestService.loadForWorkflow(request.getId());
        approved.setOneCDocumentRef("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        dataManager.save(approved);

        stubPreparedPaymentOrder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");

        PaymentRequest returned = paymentRequestService.returnFromOneC(
                request.getId(), OneCReturnAction.REVISION, null, "Вернуть");
        assertThat(returned.getStatus()).isEqualTo(PaymentRequestStatus.DRAFT);
        assertThat(returned.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void returnAllowedWhenPaymentOrderOnSignature() {
        PaymentRequest request = paymentRequest(counterparty("На подписи"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                         "Posted":true,"DeletionMark":false}
                        """);
        stubBankState("НаПодписи");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");

        PaymentRequest returned = paymentRequestService.returnFromOneC(
                request.getId(), OneCReturnAction.REVISION, null, "Вернуть");
        assertThat(returned.getStatus()).isEqualTo(PaymentRequestStatus.DRAFT);
    }

    @Test
    void financialDirectorDeleteMarksPaymentOrderDeletedWithComment() {
        PaymentRequest request = paymentRequest(counterparty("Удален ФД"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        stubPreparedPaymentOrder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        stubAttachedFiles("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");
        when(oneCOdataClient.patch(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"), anyString()))
                .thenReturn("{}");

        paymentRequestService.deleteRequest(request.getId());
        assertThat(dataManager.load(PaymentRequest.class).id(request.getId()).optional()).isEmpty();
        verify(oneCOdataClient).deleteRaw(
                eq("InformationRegister_НаличиеФайлов"),
                contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(oneCOdataClient).deleteRaw(
                eq("InformationRegister_СостоянияБанковскихДокументов"),
                contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        verify(oneCOdataClient).patch(
                eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                contains("DeletionMark"));
        verify(oneCOdataClient).delete(
                eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        verify(oneCOdataClient).delete(
                eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    }

    @Test
    void cannotDeleteWhenPaymentOrderAlreadySent() {
        PaymentRequest request = paymentRequest(counterparty("Не удалять"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                         "Posted":true,"DeletionMark":false}
                        """);
        stubBankState("Отправлено");

        assertThatThrownBy(() -> paymentRequestService.deleteRequest(request.getId()))
                .isInstanceOf(PaymentRequestException.class);
        assertThat(paymentRequestService.loadForWorkflow(request.getId()).getStatus())
                .isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        verify(oneCOdataClient, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteAfterReturnRemovesPaymentOrderAndAttachedFile() {
        PaymentRequest request = paymentRequest(counterparty("После возврата"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        stubPreparedPaymentOrder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");
        PaymentRequest returned = paymentRequestService.returnFromOneC(
                request.getId(), OneCReturnAction.REVISION, null, "Вернуть");
        assertThat(returned.getStatus()).isEqualTo(PaymentRequestStatus.DRAFT);

        stubAttachedFiles("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                "dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(oneCOdataClient.patch(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"), anyString()))
                .thenReturn("{}");

        paymentRequestService.deleteRequest(request.getId());
        assertThat(dataManager.load(PaymentRequest.class).id(request.getId()).optional()).isEmpty();
        verify(oneCOdataClient).delete(
                eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        verify(oneCOdataClient).delete(
                eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    }

    @Test
    void exportKeepsApprovedWhenOneCPostFails() {
        PaymentRequest request = paymentRequest(counterparty("Сбой 1С"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenThrow(new PaymentRequestException("1C down"));

        assertThatThrownBy(() -> paymentRequestService.exportToOneC(request.getId()))
                .isInstanceOf(PaymentRequestException.class);
        PaymentRequest persisted = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(persisted.getStatus()).isEqualTo(PaymentRequestStatus.APPROVED);
        assertThat(persisted.getOneCDocumentRef()).isNull();
    }

    @Test
    void submitWithoutAttachmentFails() {
        PaymentRequest request = paymentRequest(counterparty("Без файла"), null, "10.00", "Тест");

        assertThatThrownBy(() -> paymentRequestService.submit(request.getId()))
                .isInstanceOf(PaymentRequestException.class)
                .hasMessageContaining("Приложите");
        assertThat(paymentRequestService.loadForWorkflow(request.getId()).getStatus())
                .isEqualTo(PaymentRequestStatus.DRAFT);
    }

    @Test
    void submitWithoutBankAccountFails() {
        Counterparty counterparty = counterparty("Без счёта");
        PaymentRequest request = dataManager.create(PaymentRequest.class);
        request.setCounterparty(counterparty);
        request.setAmount(new BigDecimal("10.00"));
        request.setPurpose("Тест");
        PaymentRequest saved = dataManager.save(request);
        cleanup.add(saved);

        assertThatThrownBy(() -> paymentRequestService.submit(saved.getId()))
                .isInstanceOf(PaymentRequestException.class)
                .hasMessageContaining("счёт");
        assertThat(paymentRequestService.loadForWorkflow(saved.getId()).getStatus())
                .isEqualTo(PaymentRequestStatus.DRAFT);
    }

    @Test
    void employeeCanCancelSubmittedRequest() {
        PaymentRequest request = paymentRequest(counterparty("Отмена"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());

        PaymentRequest cancelled = paymentRequestService.cancel(request.getId());
        assertThat(cancelled.getStatus()).isEqualTo(PaymentRequestStatus.CANCELLED);
    }

    @Test
    void financialDirectorCanDeferSubmittedRequest() {
        PaymentRequest request = paymentRequest(counterparty("Отложить"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());

        LocalDate until = LocalDate.now().plusDays(5);
        PaymentRequest deferred = paymentRequestService.defer(request.getId(), until);
        assertThat(deferred.getStatus()).isEqualTo(PaymentRequestStatus.DEFERRED);
        assertThat(deferred.getDeferredUntil()).isEqualTo(until);

        PaymentRequest approved = paymentRequestService.approve(request.getId(), until.plusDays(1), "Ок");
        assertThat(approved.getStatus()).isEqualTo(PaymentRequestStatus.APPROVED);
    }

    @Test
    void cannotDeleteApprovedRequestByEmployeeRule() {
        PaymentRequest request = paymentRequest(counterparty("Удаление"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        PaymentRequest persisted = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(PaymentRequestService.isDeletable(persisted)).isFalse();
        paymentRequestService.deleteRequest(persisted.getId());
        assertThat(dataManager.load(PaymentRequest.class).id(request.getId()).optional()).isEmpty();
    }

    @Test
    void keepsLastChangedByThroughApprove() {
        PaymentRequest request = paymentRequest(counterparty("Редактор"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        User editor = dataManager.create(User.class);
        editor.setUsername("editor-" + UUID.randomUUID());
        editor.setActive(true);
        editor = dataManager.save(editor);
        cleanup.add(0, editor);

        PaymentRequest loaded = paymentRequestService.loadForWorkflow(request.getId());
        loaded.setLastChangedBy(editor);
        dataManager.save(loaded);

        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        PaymentRequest approved = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(approved.getLastChangedBy().getId()).isEqualTo(editor.getId());
    }

    @Test
    void syncKeepsSentWhenPaymentOrderPostedButNotPaid() {
        PaymentRequest request = paymentRequest(counterparty("Проведена 1С"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        when(oneCOdataClient.isConfigured()).thenReturn(true);
        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001","Date":"2026-09-22T00:00:00"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        stubBinaryStorage();
        paymentRequestService.exportToOneC(request.getId());

        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000099",
                         "Date":"2026-09-22T00:00:00","Posted":true,"DeletionMark":false}
                        """);
        when(oneCOdataClient.get(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("""
                        {"value":[{"Состояние":"Подготовлено"}]}
                        """);

        assertThat(paymentRequestService.syncExportedFromOneC()).isGreaterThanOrEqualTo(1);
        PaymentRequest sent = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(sent.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        assertThat(sent.getOneCDocumentTitle()).isEqualTo("ПП №00БП-000099 от 22.09.2026");
    }

    @Test
    void syncsPaidStatusFromPaidBankState() {
        PaymentRequest request = paymentRequest(counterparty("Оплата 1С"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        when(oneCOdataClient.isConfigured()).thenReturn(true);
        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001","Date":"2026-09-22T00:00:00"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        stubBinaryStorage();
        paymentRequestService.exportToOneC(request.getId());

        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                         "Date":"2026-09-22T00:00:00","Posted":true,"DeletionMark":false}
                        """);
        when(oneCOdataClient.get(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("""
                        {"value":[{"Состояние":"Оплачено"}]}
                        """);

        assertThat(paymentRequestService.syncExportedFromOneC()).isGreaterThanOrEqualTo(1);
        PaymentRequest paid = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(paid.getStatus()).isEqualTo(PaymentRequestStatus.PAID);
        assertThat(paid.getOneCDocumentTitle()).isEqualTo("ПП №00БП-000001 от 22.09.2026");
    }

    @Test
    void returnFromOneCMarksPaymentOrderDeletedAndSendsToRevision() {
        PaymentRequest request = paymentRequest(counterparty("Возврат"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");

        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        stubBinaryStorage();
        paymentRequestService.exportToOneC(request.getId());

        stubPreparedPaymentOrder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");

        PaymentRequest returned = paymentRequestService.returnFromOneC(
                request.getId(), OneCReturnAction.REVISION, null, "Вернуть на доработку");
        assertThat(returned.getStatus()).isEqualTo(PaymentRequestStatus.DRAFT);
        assertThat(returned.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(returned.getCfoComment()).isEqualTo("Вернуть на доработку");
        verify(oneCOdataClient).patch(
                eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("DeletionMark"));
    }

    @Test
    void cannotReturnWhenPaymentOrderAlreadySentOrPaid() {
        PaymentRequest request = paymentRequest(counterparty("Без возврата"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                         "Posted":true,"DeletionMark":false}
                        """);
        stubBankState("Отправлено");

        assertThatThrownBy(() -> paymentRequestService.returnFromOneC(
                request.getId(), OneCReturnAction.REVISION, null, "Нельзя"))
                .isInstanceOf(PaymentRequestException.class);
        PaymentRequest stillSent = paymentRequestService.loadForWorkflow(request.getId());
        assertThat(stillSent.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        assertThat(stillSent.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    void reexportRestoresDeletedPaymentOrderInsteadOfCreatingNew() {
        PaymentRequest request = paymentRequest(counterparty("Повтор"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();
        paymentRequestService.exportToOneC(request.getId());

        stubPreparedPaymentOrder("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(oneCOdataClient.patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("{}");
        paymentRequestService.returnFromOneC(request.getId(), OneCReturnAction.REVISION, null, "Доработать");
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(2), "Ок");

        PaymentRequest exportedAgain = paymentRequestService.exportToOneC(request.getId());
        assertThat(exportedAgain.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        assertThat(exportedAgain.getOneCDocumentRef()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        verify(oneCOdataClient, times(1)).post(eq("Document_ПлатежноеПоручение"), anyString());
        verify(oneCOdataClient, never()).post(eq("Document_ПлатежноеПоручение"),
                org.mockito.ArgumentMatchers.argThat(json -> json != null && json.contains("\"Posted\":true")));
        verify(oneCOdataClient).patch(
                eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("\"DeletionMark\":false"));
    }

    @Test
    void submitsWithoutBankWhenCounterpartyMissingInOneC() {
        Counterparty pending = pendingCounterparty("9900112233");
        PaymentRequest request = paymentRequestMissing(pending, nomenclature("Услуга"), "10.00", "Основание");
        attachInvoice(request);

        PaymentRequest submitted = paymentRequestService.submit(request.getId());
        assertThat(submitted.getStatus()).isEqualTo(PaymentRequestStatus.SUBMITTED);
        assertThat(submitted.isMissingInOneC()).isTrue();
        assertThat(submitted.getCounterpartyBankAccount()).isNull();
        assertThat(dataManager.load(PaymentRequestNotice.class)
                .query("select e from vshd1_PaymentRequestNotice e where e.paymentRequest.id = :id")
                .parameter("id", request.getId())
                .list()).isNotEmpty();
    }

    @Test
    void rejectsSubmitWithoutBankWhenCounterpartyIsInOneC() {
        PaymentRequest request = dataManager.create(PaymentRequest.class);
        request.setCounterparty(counterparty("Есть в 1С"));
        request.setNomenclature(nomenclature("Услуга"));
        request.setAmount(new BigDecimal("10.00"));
        request.setPurpose("Основание");
        request.setMissingInOneC(false);
        PaymentRequest saved = dataManager.save(request);
        cleanup.add(saved);
        attachInvoice(saved);

        assertThatThrownBy(() -> paymentRequestService.submit(saved.getId()))
                .isInstanceOf(PaymentRequestException.class);
    }

    @Test
    void approveBlockedWhileCounterpartyMissingInOneC() {
        Counterparty pending = pendingCounterparty("9900112244");
        PaymentRequest request = paymentRequestMissing(pending, nomenclature("Услуга"), "10.00", "Основание");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());

        assertThatThrownBy(() -> paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок"))
                .isInstanceOf(PaymentRequestException.class);
        assertThat(paymentRequestService.loadForWorkflow(request.getId()).getStatus())
                .isEqualTo(PaymentRequestStatus.SUBMITTED);
    }

    @Test
    void refreshCounterpartyFromOneCAllowsApprove() {
        String inn = "7707083893";
        Counterparty pending = pendingCounterparty(inn);
        PaymentRequest request = paymentRequestMissing(pending, nomenclature("Услуга"), "10.00", "Основание");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());

        UUID realRef = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID accountRef = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID contractRef = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.get(eq("Catalog_Контрагенты"), contains("ИНН eq"), eq(0), eq(20), eq("Description")))
                .thenReturn("""
                        {"value":[{"Ref_Key":"%s","Description":"ООО Реал","ИНН":"%s",
                         "DeletionMark":false,"IsFolder":false}]}
                        """.formatted(realRef, inn));
        when(oneCOdataClient.get(eq("Catalog_БанковскиеСчета"), anyString(), anyInt(), anyInt(),
                anyString(), anyString()))
                .thenReturn("""
                        {"value":[{"Ref_Key":"%s","Description":"Расчётный","Code":"000000001",
                         "Owner":"%s","Owner_Type":"StandardODATA.Catalog_Контрагенты",
                         "НомерСчета":"40702810100000000099","DeletionMark":false}]}
                        """.formatted(accountRef, realRef));
        when(oneCOdataClient.get(eq("InformationRegister_ОсновныеДоговорыКонтрагента"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("{\"value\":[]}");
        when(oneCOdataClient.get(eq("Catalog_ДоговорыКонтрагентов"), anyString(), anyInt(), anyInt(),
                anyString(), anyString()))
                .thenReturn("""
                        {"value":[{"Ref_Key":"%s","Description":"Основной","Code":"000000001",
                         "Owner_Key":"%s","ВидДоговора":"СПоставщиком","СтавкаНДС":"НДС20",
                         "СуммаВключаетНДС":true,"DeletionMark":false}]}
                        """.formatted(contractRef, realRef));

        PaymentRequest refreshed = paymentRequestService.refreshCounterpartyFromOneC(request.getId());
        cleanup.add(refreshed.getCounterparty());
        if (refreshed.getCounterpartyBankAccount() != null) {
            cleanup.add(refreshed.getCounterpartyBankAccount());
        }
        if (refreshed.getContract() != null) {
            cleanup.add(refreshed.getContract());
        }
        assertThat(refreshed.isMissingInOneC()).isFalse();
        assertThat(refreshed.getCounterpartyBankAccount()).isNotNull();
        assertThat(refreshed.getContract()).isNotNull();

        PaymentRequest approved = paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        assertThat(approved.getStatus()).isEqualTo(PaymentRequestStatus.APPROVED);
    }

    @Test
    void exportBlockedWhileCounterpartyMissingInOneC() {
        Counterparty pending = pendingCounterparty("9900112255");
        PaymentRequest request = paymentRequestMissing(pending, nomenclature("Услуга"), "10.00", "Основание");
        attachInvoice(request);
        PaymentRequest saved = paymentRequestService.loadForWorkflow(request.getId());
        saved.setStatus(PaymentRequestStatus.APPROVED);
        saved.setApprovedPaymentDate(LocalDate.now().plusDays(1));
        dataManager.save(saved);

        assertThatThrownBy(() -> paymentRequestService.exportToOneC(request.getId()))
                .isInstanceOf(PaymentRequestException.class);
    }

    @Test
    void exportPrepareLeavesPaymentOrderUnposted() {
        PaymentRequest request = paymentRequest(counterparty("Подготовить"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        paymentRequestService.approve(request.getId(), LocalDate.now().plusDays(1), "Ок");
        stubExportToNewPaymentOrder();

        PaymentRequest exported = paymentRequestService.exportToOneC(request.getId(), OneCExportMode.PREPARE);
        assertThat(exported.getStatus()).isEqualTo(PaymentRequestStatus.SENT_TO_1C);
        verify(oneCOdataClient, never()).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("\"Posted\":true"));
        verify(oneCOdataClient).patchRaw(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(),
                contains("Подготовлено"));
    }

    @Test
    void exportUsesDeferredDateWhenApprovedDateMissing() {
        PaymentRequest request = paymentRequest(counterparty("Отложено"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        LocalDate deferred = LocalDate.now().plusDays(7);
        PaymentRequest approved = paymentRequestService.loadForWorkflow(request.getId());
        approved.setStatus(PaymentRequestStatus.APPROVED);
        approved.setApprovedPaymentDate(null);
        approved.setDeferredUntil(deferred);
        dataManager.save(approved);
        stubExportToNewPaymentOrder();

        PaymentRequest exported = paymentRequestService.exportToOneC(request.getId(), OneCExportMode.APPROVE);
        assertThat(exported.getApprovedPaymentDate()).isEqualTo(deferred);
        verify(oneCOdataClient).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                contains("\"Posted\":true"));
    }

    @Test
    void exportUsesRequestedDateWhenOtherDatesMissing() {
        PaymentRequest request = paymentRequest(counterparty("Желаемая"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        LocalDate requested = LocalDate.now().plusDays(4);
        PaymentRequest draft = paymentRequestService.loadForWorkflow(request.getId());
        draft.setRequestedPaymentDate(requested);
        dataManager.save(draft);
        paymentRequestService.submit(request.getId());
        PaymentRequest approved = paymentRequestService.loadForWorkflow(request.getId());
        approved.setStatus(PaymentRequestStatus.APPROVED);
        approved.setApprovedPaymentDate(null);
        approved.setDeferredUntil(null);
        dataManager.save(approved);
        stubExportToNewPaymentOrder();

        PaymentRequest exported = paymentRequestService.exportToOneC(request.getId(), OneCExportMode.PREPARE);
        assertThat(exported.getApprovedPaymentDate()).isEqualTo(requested);
    }

    @Test
    void updateApprovedPaymentDatePersistsForFinancialDirector() {
        PaymentRequest request = paymentRequest(counterparty("Дата в списке"), nomenclature("Услуга"), "10.00", "Тест");
        attachInvoice(request);
        paymentRequestService.submit(request.getId());
        LocalDate paymentDate = LocalDate.now().plusDays(2);

        PaymentRequest updated = paymentRequestService.updateApprovedPaymentDate(request.getId(), paymentDate);
        assertThat(updated.getApprovedPaymentDate()).isEqualTo(paymentDate);
        assertThat(paymentRequestService.loadForWorkflow(request.getId()).getApprovedPaymentDate())
                .isEqualTo(paymentDate);
    }

    @AfterEach
    void tearDown() {
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            Object entity = cleanup.get(i);
            if (entity instanceof PaymentRequest request) {
                dataManager.load(PaymentRequestNotice.class)
                        .query("select e from vshd1_PaymentRequestNotice e where e.paymentRequest.id = :id")
                        .parameter("id", request.getId())
                        .list()
                        .forEach(notice -> dataManager.load(Id.of(notice)).optional().ifPresent(dataManager::remove));
                dataManager.load(PaymentRequestFile.class)
                        .query("select e from vshd1_PaymentRequestFile e where e.paymentRequest.id = :id")
                        .parameter("id", request.getId())
                        .list()
                        .forEach(file -> dataManager.load(Id.of(file)).optional().ifPresent(dataManager::remove));
                dataManager.load(PaymentRequest.class)
                        .id(request.getId())
                        .fetchPlan(io.jmix.core.FetchPlan.BASE)
                        .optional()
                        .ifPresent(loaded -> {
                            loaded.setStatus(PaymentRequestStatus.DRAFT);
                            dataManager.remove(dataManager.save(loaded));
                        });
            }
        }
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            Object entity = cleanup.get(i);
            if (!(entity instanceof PaymentRequest)) {
                dataManager.load(Id.of(entity)).optional().ifPresent(dataManager::remove);
            }
        }
        cleanup.clear();
    }

    private Counterparty counterparty(String name) {
        Counterparty counterparty = dataManager.create(Counterparty.class);
        counterparty.setName(name + "-" + UUID.randomUUID());
        counterparty.setInn("7701234567");
        counterparty.setKpp("770101001");
        Counterparty saved = dataManager.save(counterparty);
        cleanup.add(saved);
        return saved;
    }

    private Nomenclature nomenclature(String name) {
        Nomenclature nomenclature = dataManager.create(Nomenclature.class);
        nomenclature.setName(name + "-" + UUID.randomUUID());
        nomenclature.setCode("00000000001");
        Nomenclature saved = dataManager.save(nomenclature);
        cleanup.add(saved);
        return saved;
    }

    private CounterpartyBankAccount bankAccount(Counterparty counterparty) {
        CounterpartyBankAccount account = dataManager.create(CounterpartyBankAccount.class);
        account.setCounterparty(counterparty);
        account.setName("Расчётный");
        account.setAccountNumber("40702810100000000001");
        CounterpartyBankAccount saved = dataManager.save(account);
        cleanup.add(saved);
        return saved;
    }

    private Counterparty pendingCounterparty(String inn) {
        Counterparty counterparty = dataManager.create(Counterparty.class);
        counterparty.setName("ИНН " + inn + "-" + UUID.randomUUID().toString().substring(0, 8));
        counterparty.setInn(inn);
        Counterparty saved = dataManager.save(counterparty);
        cleanup.add(saved);
        return saved;
    }

    private PaymentRequest paymentRequestMissing(Counterparty counterparty,
                                                 Nomenclature nomenclature,
                                                 String amount,
                                                 String purpose) {
        PaymentRequest request = dataManager.create(PaymentRequest.class);
        request.setCounterparty(counterparty);
        request.setNomenclature(nomenclature);
        request.setAmount(new BigDecimal(amount));
        request.setPurpose(purpose);
        request.setRequestedPaymentDate(LocalDate.now().plusDays(3));
        request.setMissingInOneC(true);
        PaymentRequest saved = dataManager.save(request);
        cleanup.add(saved);
        return saved;
    }

    private PaymentRequest paymentRequest(Counterparty counterparty,
                                          Nomenclature nomenclature,
                                          String amount,
                                          String purpose) {
        PaymentRequest request = dataManager.create(PaymentRequest.class);
        request.setCounterparty(counterparty);
        request.setCounterpartyBankAccount(bankAccount(counterparty));
        request.setNomenclature(nomenclature);
        request.setAmount(new BigDecimal(amount));
        request.setPurpose(purpose);
        PaymentRequest saved = dataManager.save(request);
        cleanup.add(saved);
        return saved;
    }

    private void attachInvoice(PaymentRequest request) {
        FileRef fileRef = fileStorageLocator.getDefault()
                .saveStream("invoice-" + UUID.randomUUID() + ".txt",
                        new ByteArrayInputStream("invoice".getBytes(StandardCharsets.UTF_8)));
        PaymentRequestFile file = dataManager.create(PaymentRequestFile.class);
        file.setPaymentRequest(request);
        file.setContentFile(fileRef);
        file.setDescription("Счёт");
        cleanup.add(dataManager.save(file));
    }

    private void stubExportToNewPaymentOrder() {
        when(oneCOdataClient.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(oneCOdataClient.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001","Date":"2026-09-22T00:00:00"}
                        """);
        when(oneCOdataClient.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        stubBinaryStorage();
    }

    private void stubBinaryStorage() {
        when(oneCOdataClient.post(eq("Catalog_ХранилищеДвоичныхДанных"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"}
                        """);
        when(oneCOdataClient.post(eq("InformationRegister_ХранилищеФайлов"), anyString()))
                .thenReturn("{}");
        when(oneCOdataClient.patchRaw(anyString(), anyString(), anyString())).thenReturn("{}");
    }

    private void stubAttachedFiles(String paymentOrderRef, String fileRef) {
        when(oneCOdataClient.get(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                contains(paymentOrderRef), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("""
                        {"value":[{"Ref_Key":"%s","DeletionMark":false}]}
                        """.formatted(fileRef));
    }

    private void stubPreparedPaymentOrder(String ref) {
        when(oneCOdataClient.getByKey(eq("Document_ПлатежноеПоручение"), eq(ref), anyString()))
                .thenReturn("""
                        {"Ref_Key":"%s","Number":"00БП-000001",
                         "Date":"2026-09-22T00:00:00","Posted":false,"DeletionMark":false}
                        """.formatted(ref));
        stubBankState("Подготовлено");
    }

    private void stubBankState(String state) {
        when(oneCOdataClient.get(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("{\"value\":[{\"Состояние\":\"" + state + "\"}]}");
    }
}

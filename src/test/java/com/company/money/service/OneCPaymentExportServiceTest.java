package com.company.money.service;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.User;
import com.company.money.onec.OneCOdataClient;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.Messages;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OneCPaymentExportServiceTest {

    @Test
    void postsOdataPaymentOrderAndReturnsRef() {
        OneCOdataClient client = mock(OneCOdataClient.class);
        Messages messages = mock(Messages.class);
        when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(client.getByKey(eq("Catalog_Организации"), anyString(), anyString()))
                .thenReturn("""
                        {"Description":"ВОСХОД ООО","НаименованиеПолное":"ООО ВОСХОД",
                         "ИНН":"5029197341","КПП":"502901001",
                         "ОсновнойБанковскийСчет_Key":"c9971126-0165-11eb-80ff-00155d0969c6"}
                        """);
        when(client.get(eq("Catalog_Пользователи"), anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenAnswer(invocation -> {
                    String filter = invocation.getArgument(1);
                    if (filter != null && filter.contains("ФизическоеЛицо_Key")) {
                        return """
                                {"value":[{"Ref_Key":"11111111-1111-1111-1111-111111111111",
                                 "Description":"Иванов Иван","Служебный":false,"Недействителен":false}]}
                                """;
                    }
                    return """
                            {"value":[{"Ref_Key":"4ac1586e-7ecb-11ee-a2ed-fd3b53849f07","Description":"Администратор"}]}
                            """;
                });
        when(client.getByKey(eq("Catalog_Сотрудники"), anyString(), anyString()))
                .thenReturn("""
                        {"Ref_Key":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","Description":"Иванов Иван",
                         "ФизическоеЛицо_Key":"22222222-2222-2222-2222-222222222222"}
                        """);
        when(client.post(eq("Document_ПлатежноеПоручение"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001"}
                        """);
        when(client.post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}
                        """);
        when(client.post(eq("Catalog_ХранилищеДвоичныхДанных"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"}
                        """);
        when(client.post(eq("InformationRegister_ХранилищеФайлов"), anyString()))
                .thenReturn("{}");
        when(client.patch(anyString(), anyString(), anyString())).thenReturn("{}");
        when(client.patchRaw(anyString(), anyString(), anyString())).thenReturn("{}");

        FileStorageLocator fileStorageLocator = mock(FileStorageLocator.class);
        FileStorage fileStorage = mock(FileStorage.class);
        when(fileStorageLocator.getDefault()).thenReturn(fileStorage);
        FileRef fileRef = mock(FileRef.class);
        when(fileRef.getFileName()).thenReturn("invoice.pdf");
        when(fileStorage.openStream(fileRef))
                .thenReturn(new ByteArrayInputStream("invoice".getBytes(StandardCharsets.UTF_8)));
        PaymentRequestFile attachment = mock(PaymentRequestFile.class);
        when(attachment.getContentFile()).thenReturn(fileRef);
        when(attachment.getDescription()).thenReturn("Счёт");

        UUID oneCRef = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID accountRef = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID employeeRef = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Counterparty counterparty = mock(Counterparty.class);
        when(counterparty.getOneCRef()).thenReturn(oneCRef);
        when(counterparty.getName()).thenReturn("ООО Тест");
        when(counterparty.getInn()).thenReturn("7701234567");
        when(counterparty.getKpp()).thenReturn("770101001");

        CounterpartyBankAccount bankAccount = mock(CounterpartyBankAccount.class);
        when(bankAccount.getOneCRef()).thenReturn(accountRef);

        UUID contractRef = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        CounterpartyContract contract = mock(CounterpartyContract.class);
        when(contract.getOneCRef()).thenReturn(contractRef);
        when(contract.getVatRate()).thenReturn("НДС20");
        when(contract.getAmountIncludesVat()).thenReturn(true);

        User applicant = mock(User.class);
        when(applicant.getOneCEmployeeRef()).thenReturn(employeeRef);
        when(applicant.getEmployeeFullName()).thenReturn("Иванов Иван");

        PaymentRequest request = mock(PaymentRequest.class);
        when(request.getCounterparty()).thenReturn(counterparty);
        when(request.getCounterpartyBankAccount()).thenReturn(bankAccount);
        when(request.getContract()).thenReturn(contract);
        when(request.getApplicant()).thenReturn(applicant);
        when(request.getApprovedPaymentDate()).thenReturn(LocalDate.of(2026, 9, 22));
        when(request.getAmount()).thenReturn(new BigDecimal("100.5"));
        when(request.getPurpose()).thenReturn("Оплата по счёту");
        when(request.getRequestNumber()).thenReturn("ЗОП-2026-000001");
        when(request.getAttachments()).thenReturn(List.of(attachment));

        OneCPaymentExportService.CreatedPaymentOrder created =
                new OneCPaymentExportService(client, messages, fileStorageLocator).createPaymentOrder(request);

        assertThat(created.refKey()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(created.number()).isEqualTo("00БП-000001");
        assertThat(created.payload())
                .contains("Document_ПлатежноеПоручение")
                .contains("ОплатаПоставщику")
                .contains(oneCRef.toString());
        verify(client).post(eq("Document_ПлатежноеПоручение"), argThat(json ->
                json.contains("ОплатаПоставщику")
                        && json.contains("\"Posted\":false")
                        && json.contains(oneCRef.toString())
                        && json.contains(accountRef.toString())
                        && json.contains("СчетКонтрагента_Key")
                        && !json.contains("СчетКонтрагента_Type")
                        && json.contains("Контрагент_Type")
                        && json.contains("100.50")
                        && json.contains(contractRef.toString())
                        && json.contains("ДоговорКонтрагента_Key")
                        && json.contains("НДС20")
                        && json.contains("16.75")
                        && json.contains("c1405bb8-2942-11e5-874a-001e101f4da1")
                        && json.contains("СчетОрганизации_Key")
                        && json.contains("c9971126-0165-11eb-80ff-00155d0969c6")
                        && json.contains("ИННПлательщика")
                        && json.contains("5029197341")
                        && json.contains("Ответственный_Key")
                        && json.contains("11111111-1111-1111-1111-111111111111")));

        OneCPaymentExportService service = new OneCPaymentExportService(client, messages, fileStorageLocator);
        service.attachInvoiceFiles(created.refKey(), request);
        service.finishExport(request, created.refKey(), OneCExportMode.APPROVE);
        verify(client).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                argThat(json -> json.contains("\"Posted\":true")));
        verify(client).patch(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                argThat(json -> json.contains("Ответственный_Key")
                        && json.contains("11111111-1111-1111-1111-111111111111")));
        verify(client).patchRaw(eq("InformationRegister_СостоянияБанковскихДокументов"),
                argThat(key -> key.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        && key.contains("СсылкаНаОбъект_Type")
                        && key.contains("c1405bb8-2942-11e5-874a-001e101f4da1")),
                argThat(json -> json.contains("Согласовано")
                        && json.contains("\"Согласовано\":true")
                        && json.contains("11111111-1111-1111-1111-111111111111")));
        verify(client).post(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"), argThat(json ->
                json.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        && json.contains("Счёт")
                        && json.contains("ВладелецФайла_Key")
                        && !json.contains("ВладелецФайла_Type")
                        && !json.contains("ФайлХранилище")
                        && json.contains("ВИнформационнойБазе")
                        && json.contains("ДатаСоздания")
                        && json.contains("\"Автор\"")
                        && json.contains("Автор_Type")
                        && json.contains("4ac1586e-7ecb-11ee-a2ed-fd3b53849f07")
                        && json.contains("\"ИндексКартинки\":\"52\"")));
        verify(client).post(eq("Catalog_ХранилищеДвоичныхДанных"), argThat(json ->
                json.contains("ДвоичныеДанные_Base64Data")
                        && json.contains("Хеш")
                        && json.contains("application/octet-stream")));
        verify(client).post(eq("InformationRegister_ХранилищеФайлов"), argThat(json ->
                json.contains("dddddddd-dddd-dddd-dddd-dddddddddddd")
                        && json.contains("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")
                        && json.contains("Файл_Type")));
    }

    @Test
    void deletesPaymentOrderAndAttachedFileWhenNotSent() {
        OneCOdataClient client = mock(OneCOdataClient.class);
        Messages messages = mock(Messages.class);
        when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(client.getOrganizationKey()).thenReturn("c1405bb8-2942-11e5-874a-001e101f4da1");
        when(client.getByKey(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), anyString()))
                .thenReturn("""
                        {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                         "Posted":false,"DeletionMark":false}
                        """);
        when(client.get(eq("InformationRegister_СостоянияБанковскихДокументов"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("{\"value\":[{\"Состояние\":\"Подготовлено\"}]}");
        when(client.get(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                anyString(), anyInt(), anyInt(), nullable(String.class), anyString()))
                .thenReturn("""
                        {"value":[{"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd"}]}
                        """);
        when(client.patch(anyString(), anyString(), anyString())).thenReturn("{}");

        new OneCPaymentExportService(client, messages, mock(FileStorageLocator.class))
                .deletePaymentOrderWithAttachments("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Удален ФД");

        verify(client).patch(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                argThat(json -> json.contains("DeletionMark")));
        verify(client).delete(eq("Catalog_ПлатежноеПоручениеПрисоединенныеФайлы"),
                eq("dddddddd-dddd-dddd-dddd-dddddddddddd"));
        verify(client).deleteRaw(eq("InformationRegister_НаличиеФайлов"),
                argThat(key -> key.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        && key.contains("ОбъектСФайлами")));
        verify(client).deleteRaw(eq("InformationRegister_СостоянияБанковскихДокументов"),
                argThat(key -> key.contains("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
                        && key.contains("Организация_Key")));
        verify(client).delete(eq("Document_ПлатежноеПоручение"),
                eq("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    }
}

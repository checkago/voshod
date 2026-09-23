package com.company.money.service;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.User;
import com.company.money.onec.OneCOdataClient;
import com.company.money.onec.OneCOdataParser;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.Messages;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OneCPaymentExportService {

    static final String PAYMENT_ORDER_ENTITY = "Document_ПлатежноеПоручение";
    static final String ATTACHED_FILES_ENTITY = "Catalog_ПлатежноеПоручениеПрисоединенныеФайлы";
    static final String BANK_DOCUMENT_STATES = "InformationRegister_СостоянияБанковскихДокументов";
    static final String BANK_EXCHANGE_STATES = "InformationRegister_СостоянияОбменСБанками";
    static final String FILES_PRESENCE = "InformationRegister_НаличиеФайлов";
    static final String BANK_WRITE_OFFS = "Document_СписаниеСРасчетногоСчета";
    static final String ORGANIZATION_ENTITY = "Catalog_Организации";
    static final String CURRENCY_ENTITY = "Catalog_Валюты";
    static final String USERS_ENTITY = "Catalog_Пользователи";
    static final String EMPLOYEES_ENTITY = "Catalog_Сотрудники";
    static final String BINARY_STORAGE_ENTITY = "Catalog_ХранилищеДвоичныхДанных";
    private static final String EMPTY_GUID = "00000000-0000-0000-0000-000000000000";
    static final String FILE_STORAGE_REGISTER = "InformationRegister_ХранилищеФайлов";
    private static final String COUNTERPARTY_TYPE = "StandardODATA.Catalog_Контрагенты";
    private static final String PAYMENT_ORDER_TYPE = "StandardODATA.Document_ПлатежноеПоручение";
    private static final String USER_TYPE = "StandardODATA.Catalog_Пользователи";
    private static final String ATTACHED_FILE_TYPE = "StandardODATA.Catalog_ПлатежноеПоручениеПрисоединенныеФайлы";
    private static final String RUB_CODE = "643";
    private static final JsonMapper JSON = JsonMapper.shared();

    private final OneCOdataClient odataClient;
    private final Messages messages;
    private final FileStorageLocator fileStorageLocator;

    public OneCPaymentExportService(OneCOdataClient odataClient,
                                    Messages messages,
                                    FileStorageLocator fileStorageLocator) {
        this.odataClient = odataClient;
        this.messages = messages;
        this.fileStorageLocator = fileStorageLocator;
    }

    public CreatedPaymentOrder createPaymentOrder(PaymentRequest request) {
        Counterparty counterparty = request.getCounterparty();
        if (counterparty == null || counterparty.getOneCRef() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needCounterparty"));
        }
        String organizationKey = resolveOrganizationKey();
        Map<String, Object> body = buildOdataBody(request, counterparty, organizationKey);
        String requestJson = writeJson(body);
        String responseJson = odataClient.post(PAYMENT_ORDER_ENTITY, requestJson);
        OneCOdataParser.CreatedDocument created = OneCOdataParser.parseCreatedDocument(responseJson);
        if (created.refKey() == null || created.refKey().isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.invalidResponse"));
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("entity", PAYMENT_ORDER_ENTITY);
        stored.put("Number", created.number());
        stored.put("Date", created.date());
        stored.put("Ref_Key", created.refKey());
        stored.put("request", body);
        LocalDate documentDate = created.date() != null ? created.date() : request.getApprovedPaymentDate();
        return new CreatedPaymentOrder(created.refKey(), created.number(), documentDate, writeJson(stored));
    }

    Map<String, Object> buildOdataBody(PaymentRequest request, Counterparty counterparty, String organizationKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Date", request.getApprovedPaymentDate() + "T00:00:00");
        body.put("Posted", false);
        body.put("ВидОперации", "ОплатаПоставщику");
        body.put("Организация_Key", organizationKey);
        body.put("Контрагент", counterparty.getOneCRef().toString());
        body.put("Контрагент_Type", COUNTERPARTY_TYPE);
        CounterpartyBankAccount bankAccount = request.getCounterpartyBankAccount();
        if (bankAccount != null && bankAccount.getOneCRef() != null) {
            body.put("СчетКонтрагента_Key", bankAccount.getOneCRef().toString());
        }
        body.put("СуммаДокумента", scaleAmount(request.getAmount()));
        body.put("НазначениеПлатежа", trimTo(request.getPurpose(), 210));
        body.put("Комментарий", trimTo("Заявка " + request.getRequestNumber(), 210));
        body.put("ОчередностьПлатежа", 5);
        applyContract(body, request);
        applyResponsible(body, request);
        applyPayerDetails(body, organizationKey);
        applyDefaultCurrency(body);
        String inn = trimTo(counterparty.getInn(), 12);
        if (inn != null) {
            body.put("ИННПолучателя", inn);
        }
        String kpp = trimTo(counterparty.getKpp(), 9);
        if (kpp != null) {
            body.put("КПППолучателя", kpp);
        }
        String recipient = counterparty.getFullName();
        if (recipient == null || recipient.isBlank()) {
            recipient = counterparty.getName();
        }
        recipient = trimTo(recipient, 500);
        if (recipient != null) {
            body.put("ТекстПолучателя", recipient);
        }
        return body;
    }

    void applyContract(Map<String, Object> body, PaymentRequest request) {
        CounterpartyContract contract = request.getContract();
        if (contract == null || contract.getOneCRef() == null) {
            body.put("СтавкаНДС", "БезНДС");
            return;
        }
        body.put("ДоговорКонтрагента_Key", contract.getOneCRef().toString());
        String vatRate = contract.getVatRate();
        if (vatRate == null || vatRate.isBlank()) {
            vatRate = "БезНДС";
        }
        body.put("СтавкаНДС", vatRate);
        boolean includesVat = Boolean.TRUE.equals(contract.getAmountIncludesVat())
                || vatRate.contains("_");
        BigDecimal vatAmount = vatAmount(scaleAmount(request.getAmount()), vatRate, includesVat);
        body.put("СуммаНДС", vatAmount);
        UUID currencyKey = contract.getCurrencyKey();
        if (currencyKey != null) {
            body.put("ВалютаДокумента_Key", currencyKey.toString());
        }
    }

    static BigDecimal vatAmount(BigDecimal documentAmount, String vatRate, boolean includesVat) {
        int percent = vatPercent(vatRate);
        if (percent <= 0 || documentAmount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rate = BigDecimal.valueOf(percent);
        if (includesVat) {
            return documentAmount.multiply(rate)
                    .divide(BigDecimal.valueOf(100L + percent), 2, RoundingMode.HALF_UP);
        }
        return documentAmount.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    void applyResponsible(Map<String, Object> body, PaymentRequest request) {
        User actor = request.getLastChangedBy() != null ? request.getLastChangedBy() : request.getApplicant();
        String userKey = resolveOneCUserKey(actor);
        if (userKey != null) {
            body.put("Ответственный_Key", userKey);
        }
    }

    private String resolveOneCUserKey(User user) {
        if (user == null) {
            return null;
        }
        String personKey = readPersonKey(user.getOneCEmployeeRef());
        if (personKey != null) {
            String byPerson = findUserRef(
                    "DeletionMark eq false and Недействителен eq false and ФизическоеЛицо_Key eq guid'"
                            + personKey + "'");
            if (byPerson != null) {
                return byPerson;
            }
        }
        for (String name : userNameCandidates(user)) {
            String found = findUserByDescription(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<String> userNameCandidates(User user) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        addName(names, user.getEmployeeFullName());
        addName(names, joined(user.getLastName(), user.getFirstName()));
        addName(names, joined(user.getFirstName(), user.getLastName()));
        addName(names, user.getLastName());
        addName(names, user.getUsername());
        return List.copyOf(names);
    }

    private static void addName(Set<String> names, String value) {
        if (value != null && !value.isBlank()) {
            names.add(value.trim());
        }
    }

    private static String joined(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first.trim() + " " + second.trim();
    }

    private String readPersonKey(UUID employeeRef) {
        if (employeeRef == null) {
            return null;
        }
        try {
            String json = odataClient.getByKey(EMPLOYEES_ENTITY, employeeRef.toString(),
                    "Ref_Key,Description,ФизическоеЛицо_Key");
            JsonNode node = firstJsonRow(json);
            if (node == null) {
                return null;
            }
            String personKey = text(node, "ФизическоеЛицо_Key", "ФизическоеЛицо");
            return isRealGuid(personKey) ? personKey : null;
        } catch (PaymentRequestException | JacksonException ignored) {
            return null;
        }
    }

    private String findUserByDescription(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String escaped = name.trim().replace("'", "''");
        return findUserRef("DeletionMark eq false and Недействителен eq false and Description eq '" + escaped + "'");
    }

    private String findUserRef(String filter) {
        try {
            String json = odataClient.get(USERS_ENTITY, filter, 0, 5, null,
                    "Ref_Key,Description,Служебный,Недействителен");
            return firstUserRef(json);
        } catch (PaymentRequestException | JacksonException ignored) {
            return null;
        }
    }

    private static String firstUserRef(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode root = JSON.readTree(json);
        JsonNode value = root.get("value");
        if (value != null && value.isArray()) {
            for (JsonNode row : value) {
                if (flag(row, "Служебный") || flag(row, "Недействителен")) {
                    continue;
                }
                String ref = text(row, "Ref_Key", "Ref");
                if (ref != null) {
                    return ref;
                }
            }
            return null;
        }
        if (flag(root, "Служебный") || flag(root, "Недействителен")) {
            return null;
        }
        return text(root, "Ref_Key", "Ref");
    }

    private static boolean flag(JsonNode row, String name) {
        if (row == null) {
            return false;
        }
        JsonNode node = row.get(name);
                return node != null && node.isBoolean() && node.asBoolean();
    }

    private static boolean isRealGuid(String value) {
        return value != null && !value.isBlank() && !EMPTY_GUID.equals(value);
    }

    static int vatPercent(String vatRate) {
        if (vatRate == null || vatRate.isBlank() || "БезНДС".equals(vatRate) || "НДС0".equals(vatRate)) {
            return 0;
        }
        String digits = vatRate.replaceAll("\\D+", " ").trim();
        if (digits.isEmpty()) {
            return 0;
        }
        String first = digits.split("\\s+")[0];
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public OneCOdataParser.PaymentOrderState getPaymentOrder(String refKey) {
        if (refKey == null || refKey.isBlank()) {
            return null;
        }
        String body = odataClient.getByKey(PAYMENT_ORDER_ENTITY, refKey,
                "Ref_Key,Number,Date,Posted,DeletionMark");
        OneCOdataParser.PaymentOrderState state = OneCOdataParser.parsePaymentOrder(body);
        if (state == null) {
            return null;
        }
        String bankState = readBankState(refKey);
        boolean paid = OneCOdataParser.isPaidBankState(bankState) || state.paid() || hasPostedWriteOff(refKey);
        return state.withBankState(bankState).withPaid(paid);
    }

    public CreatedPaymentOrder restorePaymentOrder(PaymentRequest request) {
        String refKey = request.getOneCDocumentRef();
        if (refKey == null || refKey.isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.invalidResponse"));
        }
        Counterparty counterparty = request.getCounterparty();
        if (counterparty == null || counterparty.getOneCRef() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needCounterparty"));
        }
        OneCOdataParser.PaymentOrderState current = getPaymentOrder(refKey);
        if (current != null && current.posted()) {
            odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":false}");
        }
        odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"DeletionMark\":false}");
        String organizationKey = resolveOrganizationKey();
        Map<String, Object> body = buildOdataBody(request, counterparty, organizationKey);
        odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, writeJson(body));
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("entity", PAYMENT_ORDER_ENTITY);
        stored.put("Number", current != null ? current.number() : null);
        stored.put("Date", current != null ? current.date() : request.getApprovedPaymentDate());
        stored.put("Ref_Key", refKey);
        stored.put("request", body);
        String number = current != null ? current.number() : request.getOneCDocumentNumber();
        LocalDate documentDate = current != null && current.date() != null
                ? current.date()
                : request.getApprovedPaymentDate();
        return new CreatedPaymentOrder(refKey, number, documentDate, writeJson(stored));
    }

    public void postPaymentOrder(String refKey) {
        if (refKey == null || refKey.isBlank()) {
            return;
        }
        odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":true}");
    }

    public void finishExport(PaymentRequest request, String refKey, OneCExportMode mode) {
        if (mode == OneCExportMode.APPROVE) {
            postPaymentOrder(refKey);
        }
        User actor = request.getLastChangedBy() != null ? request.getLastChangedBy() : request.getApplicant();
        String responsibleKey = resolveOneCUserKey(actor);
        patchResponsible(refKey, responsibleKey);
        writeBankDocumentState(refKey, resolveOrganizationKey(), mode, responsibleKey);
    }

    void patchResponsible(String refKey, String responsibleKey) {
        if (refKey == null || refKey.isBlank() || responsibleKey == null || responsibleKey.isBlank()) {
            return;
        }
        odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Ответственный_Key\":\"" + responsibleKey + "\"}");
    }

    void writeBankDocumentState(String refKey, String organizationKey, OneCExportMode mode, String responsibleKey) {
        if (refKey == null || refKey.isBlank() || organizationKey == null || organizationKey.isBlank()) {
            return;
        }
        boolean approve = mode == OneCExportMode.APPROVE;
        String state = approve ? "Согласовано" : "Подготовлено";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Состояние", state);
        body.put("Согласовано", approve);
        if (responsibleKey != null && !responsibleKey.isBlank()) {
            body.put("Ответственный_Key", responsibleKey);
        }
        String rawKey = "Организация_Key=guid'" + organizationKey
                + "',СсылкаНаОбъект=guid'" + refKey
                + "',СсылкаНаОбъект_Type='" + PAYMENT_ORDER_TYPE + "'";
        try {
            odataClient.patchRaw(BANK_DOCUMENT_STATES, rawKey, writeJson(body));
        } catch (PaymentRequestException ex) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("Организация_Key", organizationKey);
            created.put("СсылкаНаОбъект", refKey);
            created.put("СсылкаНаОбъект_Type", PAYMENT_ORDER_TYPE);
            created.putAll(body);
            try {
                odataClient.post(BANK_DOCUMENT_STATES, writeJson(created));
            } catch (PaymentRequestException ignored) {
                throw ex;
            }
        }
    }

    String readBankState(String refKey) {
        try {
            String json = odataClient.get(
                    BANK_DOCUMENT_STATES,
                    "СсылкаНаОбъект eq guid'" + refKey + "'"
                            + " and СсылкаНаОбъект_Type eq '" + PAYMENT_ORDER_TYPE + "'",
                    0,
                    1,
                    null,
                    "Состояние,СсылкаНаОбъект");
            return OneCOdataParser.parseBankDocumentState(json);
        } catch (PaymentRequestException ex) {
            return null;
        }
    }

    private boolean hasPostedWriteOff(String refKey) {
        try {
            String json = odataClient.get(
                    BANK_WRITE_OFFS,
                    "ДокументОснование eq guid'" + refKey + "'"
                            + " and ДокументОснование_Type eq '" + PAYMENT_ORDER_TYPE + "'"
                            + " and Posted eq true and DeletionMark eq false",
                    0,
                    1,
                    null,
                    "Ref_Key,Posted,DeletionMark");
            return OneCOdataParser.parsePostedWriteOff(json);
        } catch (PaymentRequestException ex) {
            return false;
        }
    }

    public void deletePaymentOrderWithAttachments(String refKey, String comment) {
        if (refKey == null || refKey.isBlank()) {
            return;
        }
        OneCOdataParser.PaymentOrderState state = null;
        try {
            state = getPaymentOrder(refKey);
        } catch (PaymentRequestException ignored) {
        }
        if (state != null && state.blocksReturn()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotReturnFromOneC"));
        }
        deleteAttachedFiles(refKey);
        deleteDependentRegisters(refKey);
        unpostPaymentOrder(refKey);
        try {
            odataClient.delete(PAYMENT_ORDER_ENTITY, refKey);
            return;
        } catch (PaymentRequestException ignored) {
        }
        try {
            markPaymentOrderDeleted(refKey, comment);
        } catch (PaymentRequestException ignored) {
        }
        odataClient.delete(PAYMENT_ORDER_ENTITY, refKey);
    }

    private void deleteDependentRegisters(String refKey) {
        tryDeleteRaw(FILES_PRESENCE,
                "ОбъектСФайлами=guid'" + refKey + "',ОбъектСФайлами_Type='" + PAYMENT_ORDER_TYPE + "'");
        tryDeleteRaw(BANK_EXCHANGE_STATES,
                "СсылкаНаОбъект=guid'" + refKey + "',СсылкаНаОбъект_Type='" + PAYMENT_ORDER_TYPE + "'");
        String organizationKey = odataClient.getOrganizationKey();
        if (organizationKey != null && !organizationKey.isBlank()) {
            tryDeleteRaw(BANK_DOCUMENT_STATES,
                    "Организация_Key=guid'" + organizationKey
                            + "',СсылкаНаОбъект=guid'" + refKey
                            + "',СсылкаНаОбъект_Type='" + PAYMENT_ORDER_TYPE + "'");
        }
    }

    private void tryDeleteRaw(String entitySet, String rawKey) {
        try {
            odataClient.deleteRaw(entitySet, rawKey);
        } catch (PaymentRequestException ignored) {
        }
    }

    private void unpostPaymentOrder(String refKey) {
        try {
            OneCOdataParser.PaymentOrderState state = getPaymentOrder(refKey);
            if (state != null && state.posted()) {
                odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":false}");
            }
        } catch (PaymentRequestException ignored) {
            try {
                odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":false}");
            } catch (PaymentRequestException ignoredAgain) {
            }
        }
    }

    private void deleteAttachedFiles(String paymentOrderRef) {
        try {
            String json = odataClient.get(
                    ATTACHED_FILES_ENTITY,
                    "ВладелецФайла_Key eq guid'" + paymentOrderRef + "'",
                    0,
                    50,
                    null,
                    "Ref_Key,DeletionMark");
            for (String fileRef : OneCOdataParser.parseRefKeys(json)) {
                try {
                    odataClient.patch(ATTACHED_FILES_ENTITY, fileRef, "{\"DeletionMark\":true}");
                } catch (PaymentRequestException ignored) {
                }
                tryDelete(ATTACHED_FILES_ENTITY, fileRef);
            }
        } catch (PaymentRequestException ignored) {
        }
    }

    private void tryDelete(String entitySet, String key) {
        try {
            odataClient.delete(entitySet, key);
        } catch (PaymentRequestException ignored) {
        }
    }

    public void markPaymentOrderDeleted(String refKey) {
        markPaymentOrderDeleted(refKey, null);
    }

    public void markPaymentOrderDeleted(String refKey, String comment) {
        if (refKey == null || refKey.isBlank()) {
            return;
        }
        try {
            OneCOdataParser.PaymentOrderState state = getPaymentOrder(refKey);
            if (state != null && state.posted()) {
                odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":false}");
            }
        } catch (PaymentRequestException ignored) {
            odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, "{\"Posted\":false}");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("DeletionMark", true);
        if (comment != null && !comment.isBlank()) {
            body.put("Комментарий", trimTo(comment, 210));
        }
        odataClient.patch(PAYMENT_ORDER_ENTITY, refKey, writeJson(body));
    }

    public boolean isConfigured() {
        return odataClient.isConfigured();
    }

    public void attachInvoiceFiles(String paymentOrderRef, PaymentRequest request) {
        List<PaymentRequestFile> files = request.getAttachments();
        if (files == null || files.isEmpty()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needAttachment"));
        }
        for (PaymentRequestFile file : files) {
            attachInvoiceFile(paymentOrderRef, file);
        }
    }

    private void attachInvoiceFile(String paymentOrderRef, PaymentRequestFile file) {
        FileRef fileRef = file.getContentFile();
        if (fileRef == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needAttachment"));
        }
        byte[] content;
        try (var stream = fileStorageLocator.getDefault().openStream(fileRef)) {
            content = stream.readAllBytes();
        } catch (Exception ex) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.attachFailed"));
        }
        String fileName = fileRef.getFileName();
        String createdAt = odataDateTime();
        Map<String, Object> fileBody = new LinkedHashMap<>();
        fileBody.put("Description", trimTo(firstNonBlank(file.getDescription(), fileName), 150));
        fileBody.put("ВладелецФайла_Key", paymentOrderRef);
        fileBody.put("Расширение", trimTo(extensionOf(fileName), 10));
        fileBody.put("Размер", String.valueOf(content.length));
        fileBody.put("ТипХраненияФайла", "ВИнформационнойБазе");
        fileBody.put("ДатаСоздания", createdAt);
        fileBody.put("ДатаМодификацииУниверсальная", createdAt);
        fileBody.put("Зашифрован", false);
        fileBody.put("ПодписанЭП", false);
        fileBody.put("ХранитьВерсии", false);
        fileBody.put("ИндексКартинки", String.valueOf(pictureIndex(extensionOf(fileName))));
        fileBody.put("СтатусИзвлеченияТекста", "НеИзвлечен");
        String authorKey = resolveAuthorKey();
        if (authorKey != null) {
            fileBody.put("Автор", authorKey);
            fileBody.put("Автор_Type", USER_TYPE);
        }
        String fileRefKey;
        try {
            OneCOdataParser.CreatedDocument createdFile =
                    OneCOdataParser.parseCreatedDocument(odataClient.post(ATTACHED_FILES_ENTITY, writeJson(fileBody)));
            fileRefKey = createdFile.refKey();
        } catch (PaymentRequestException ex) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/onec.error.attachFailed") + ": " + ex.getMessage());
        }
        if (fileRefKey == null || fileRefKey.isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.attachFailed"));
        }
        storeBinary(fileRefKey, content);
    }

    private void storeBinary(String attachedFileRef, byte[] content) {
        Map<String, Object> binaryBody = new LinkedHashMap<>();
        binaryBody.put("Размер", String.valueOf(content.length));
        binaryBody.put("Хеш", sha256Base64(content));
        binaryBody.put("ДвоичныеДанные_Type", "application/octet-stream");
        binaryBody.put("ДвоичныеДанные_Base64Data", Base64.getEncoder().encodeToString(content));
        OneCOdataParser.CreatedDocument stored;
        try {
            stored = OneCOdataParser.parseCreatedDocument(
                    odataClient.post(BINARY_STORAGE_ENTITY, writeJson(binaryBody)));
        } catch (PaymentRequestException ex) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/onec.error.attachFailed") + ": " + ex.getMessage());
        }
        if (stored.refKey() == null || stored.refKey().isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.attachFailed"));
        }
        Map<String, Object> registerBody = new LinkedHashMap<>();
        registerBody.put("Файл", attachedFileRef);
        registerBody.put("Файл_Type", ATTACHED_FILE_TYPE);
        registerBody.put("ХранилищеДвоичныхДанных_Key", stored.refKey());
        try {
            odataClient.post(FILE_STORAGE_REGISTER, writeJson(registerBody));
        } catch (PaymentRequestException ex) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/onec.error.attachFailed") + ": " + ex.getMessage());
        }
    }

    private static String sha256Base64(byte[] content) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    void applyPayerDetails(Map<String, Object> body, String organizationKey) {
        if (organizationKey == null || organizationKey.isBlank()) {
            return;
        }
        try {
            String json = odataClient.getByKey(ORGANIZATION_ENTITY, organizationKey,
                    "Description,НаименованиеПолное,ИНН,КПП,ОсновнойБанковскийСчет_Key");
            JsonNode node = firstJsonRow(json);
            if (node == null) {
                return;
            }
            String payer = firstNonBlank(text(node, "НаименованиеПолное"), text(node, "Description"));
            if (payer != null) {
                body.put("ТекстПлательщика", trimTo(payer, 500));
            }
            String inn = trimTo(text(node, "ИНН"), 12);
            if (inn != null) {
                body.put("ИННПлательщика", inn);
            }
            String kpp = trimTo(text(node, "КПП"), 9);
            if (kpp != null) {
                body.put("КПППлательщика", kpp);
            }
            String bankAccount = text(node, "ОсновнойБанковскийСчет_Key");
            if (bankAccount != null && !EMPTY_GUID.equals(bankAccount)) {
                body.put("СчетОрганизации_Key", bankAccount);
            }
        } catch (PaymentRequestException | JacksonException ignored) {
        }
    }

    void applyDefaultCurrency(Map<String, Object> body) {
        if (body.containsKey("ВалютаДокумента_Key")) {
            return;
        }
        try {
            String json = odataClient.get(CURRENCY_ENTITY, "Code eq '" + RUB_CODE + "'",
                    0, 1, null, "Ref_Key,Code");
            String ref = firstRefKey(json);
            if (ref != null) {
                body.put("ВалютаДокумента_Key", ref);
            }
        } catch (PaymentRequestException | JacksonException ignored) {
        }
    }

    private String resolveAuthorKey() {
        try {
            String json = odataClient.get(USERS_ENTITY, "Description eq 'Администратор'",
                    0, 1, null, "Ref_Key,Description");
            String ref = firstRefKey(json);
            if (ref != null) {
                return ref;
            }
            json = odataClient.get(USERS_ENTITY, null, 0, 1, null, "Ref_Key,Description");
            return firstRefKey(json);
        } catch (PaymentRequestException | JacksonException ignored) {
            return null;
        }
    }

    private static int pictureIndex(String extension) {
        if (extension != null && "pdf".equalsIgnoreCase(extension)) {
            return 52;
        }
        return 2;
    }

    private static String odataDateTime() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    }

    private static JsonNode firstJsonRow(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode root = JSON.readTree(json);
        JsonNode value = root.get("value");
        if (value != null && value.isArray() && value.size() > 0) {
            return value.get(0);
        }
        return root;
    }

    private static String firstRefKey(String json) {
        JsonNode node = firstJsonRow(json);
        return node == null ? null : text(node, "Ref_Key", "Ref");
    }

    private static String text(JsonNode row, String... names) {
        for (String name : names) {
            JsonNode node = row.get(name);
            if (node != null && !node.isNull()) {
                String value = node.asString();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "bin";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "bin";
        }
        return fileName.substring(dot + 1);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String resolveOrganizationKey() {
        String configured = odataClient.getOrganizationKey();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        throw new PaymentRequestException(messages.getMessage("com.company.money/onec.error.noOrganization"));
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimTo(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.exportPayload"));
        }
    }

    public record CreatedPaymentOrder(String refKey, String number, LocalDate date, String payload) {
    }
}

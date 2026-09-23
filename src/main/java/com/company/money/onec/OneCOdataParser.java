package com.company.money.onec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OneCOdataParser {

    private static final JsonMapper JSON = JsonMapper.shared();

    private OneCOdataParser() {
    }

    static List<OneCCatalogItem> parse(String body) {
        JsonNode root = JSON.readTree(body);
        JsonNode rows = root.get("value");
        if (rows == null || !rows.isArray()) {
            JsonNode d = root.get("d");
            if (d != null) {
                rows = d.get("results");
            }
        }
        List<OneCCatalogItem> items = new ArrayList<>();
        if (rows == null || !rows.isArray()) {
            return items;
        }
        for (JsonNode row : rows) {
            if (bool(row, "IsFolder") || bool(row, "DeletionMark")) {
                continue;
            }
            UUID ref = uuid(row, "Ref_Key", "Ref");
            String name = text(row, "Description", "Наименование");
            if (ref == null || name == null || name.isBlank()) {
                continue;
            }
            items.add(new OneCCatalogItem(
                    ref,
                    text(row, "Code", "Код"),
                    name,
                    text(row, "НаименованиеПолное", "Description_Full"),
                    text(row, "ИНН", "INN"),
                    text(row, "КПП", "KPP"),
                    text(row, "Артикул", "Article"),
                    bool(row, "Услуга") || bool(row, "Service")
            ));
        }
        return items;
    }

    static List<OneCBankAccountItem> parseBankAccounts(String body) {
        JsonNode root = JSON.readTree(body);
        JsonNode rows = root.get("value");
        if (rows == null || !rows.isArray()) {
            JsonNode d = root.get("d");
            if (d != null) {
                rows = d.get("results");
            }
        }
        List<OneCBankAccountItem> items = new ArrayList<>();
        if (rows == null || !rows.isArray()) {
            return items;
        }
        for (JsonNode row : rows) {
            if (bool(row, "DeletionMark")) {
                continue;
            }
            UUID ref = uuid(row, "Ref_Key", "Ref");
            String accountNumber = text(row, "НомерСчета");
            String name = text(row, "Description", "Наименование");
            if (name == null || name.isBlank()) {
                name = accountNumber;
            }
            if (ref == null || name == null || name.isBlank()) {
                continue;
            }
            items.add(new OneCBankAccountItem(
                    ref,
                    text(row, "Code", "Код"),
                    name,
                    accountNumber,
                    uuid(row, "Owner", "Owner_Key")
            ));
        }
        return items;
    }

    public static CreatedDocument parseCreatedDocument(String body) {
        if (body == null || body.isBlank()) {
            return new CreatedDocument(null, null, null);
        }
        JsonNode root = JSON.readTree(body);
        return new CreatedDocument(
                text(root, "Ref_Key", "Ref"),
                text(root, "Number"),
                localDate(root, "Date"));
    }

    public static PaymentOrderState parsePaymentOrder(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root = JSON.readTree(body);
        JsonNode node = firstRow(root);
        if (node == null) {
            return null;
        }
        String refKey = text(node, "Ref_Key", "Ref");
        if (refKey == null) {
            return null;
        }
        String bankState = normalizeBankState(text(node, "Состояние"));
        return new PaymentOrderState(
                refKey,
                text(node, "Number"),
                localDate(node, "Date"),
                bool(node, "Posted"),
                bool(node, "DeletionMark"),
                isPaidBankState(bankState),
                bankState);
    }

    public static String parseBankDocumentState(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        for (JsonNode row : rows(body)) {
            String state = normalizeBankState(text(row, "Состояние"));
            if (state != null) {
                return state;
            }
        }
        return null;
    }

    public static boolean parsePaidBankState(String body) {
        return isPaidBankState(parseBankDocumentState(body));
    }

    public static boolean parsePostedWriteOff(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        for (JsonNode row : rows(body)) {
            if (bool(row, "Posted") && !bool(row, "DeletionMark")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPaidBankState(String state) {
        return "Оплачено".equals(normalizeBankState(state));
    }

    public static boolean isPreparedBankState(String state) {
        return "Подготовлено".equals(normalizeBankState(state));
    }

    public static boolean isLockedBankState(String state) {
        String normalized = normalizeBankState(state);
        return "Отправлено".equals(normalized) || "Оплачено".equals(normalized);
    }

    static String normalizeBankState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        int dot = state.lastIndexOf('.');
        int slash = state.lastIndexOf('/');
        int cut = Math.max(dot, slash);
        return cut >= 0 ? state.substring(cut + 1) : state;
    }

    static List<OneCContractItem> parseContracts(String body) {
        List<OneCContractItem> items = new ArrayList<>();
        for (JsonNode row : rows(body)) {
            if (bool(row, "DeletionMark")) {
                continue;
            }
            UUID ref = uuid(row, "Ref_Key", "Ref");
            String name = text(row, "Description", "Наименование");
            if (ref == null || name == null || name.isBlank()) {
                continue;
            }
            items.add(new OneCContractItem(
                    ref,
                    text(row, "Code", "Код"),
                    name,
                    uuid(row, "Owner", "Owner_Key"),
                    text(row, "ВидДоговора"),
                    text(row, "СтавкаНДС"),
                    bool(row, "СуммаВключаетНДС"),
                    uuid(row, "ВалютаВзаиморасчетов_Key", "Валюта_Key", "ВалютаДокумента_Key")
            ));
        }
        return items;
    }

    static UUID parseMainContractRef(String body) {
        for (JsonNode row : rows(body)) {
            UUID contract = uuid(row, "Договор_Key", "Договор");
            if (contract != null) {
                return contract;
            }
        }
        return null;
    }

    public static List<String> parseRefKeys(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            List<String> keys = new ArrayList<>();
            for (JsonNode row : rows(body)) {
                String ref = text(row, "Ref_Key", "Ref");
                if (ref != null && !ref.isBlank()) {
                    keys.add(ref);
                }
            }
            return List.copyOf(keys);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public static String parseError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException ignored) {
            return htmlErrorText(body);
        }
        JsonNode message = root.path("odata.error").path("message");
        if (message.isMissingNode() || message.isNull()) {
            return htmlErrorText(body);
        }
        if (message.isTextual()) {
            String value = message.asString();
            return value == null || value.isBlank() ? htmlErrorText(body) : value;
        }
        String value = text(message, "value");
        return value == null ? htmlErrorText(body) : value;
    }

    private static String htmlErrorText(String body) {
        String trimmed = body.strip();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '{' || trimmed.charAt(0) == '[') {
            return null;
        }
        int titleStart = indexOfIgnoreCase(trimmed, "<title>");
        int titleEnd = indexOfIgnoreCase(trimmed, "</title>");
        if (titleStart >= 0 && titleEnd > titleStart) {
            String title = trimmed.substring(titleStart + 7, titleEnd).strip();
            return title.isEmpty() ? null : title;
        }
        return null;
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase().indexOf(needle);
    }

    static OneCInnRequisites parseEgrulRequisites(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode legal = root.get("СвЮЛ");
        if (legal != null && legal.isObject()) {
            return parseLegalRequisites(legal);
        }
        JsonNode entrepreneur = root.get("СвИП");
        if (entrepreneur != null && entrepreneur.isObject()) {
            return parseEntrepreneurRequisites(entrepreneur);
        }
        return parseNalogSearchResult(body);
    }

    static String parseNalogSearchToken(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        if (bool(root, "captchaRequired")) {
            return null;
        }
        return text(root, "t");
    }

    static OneCInnRequisites parseNalogSearchResult(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        JsonNode rows = root.get("rows");
        if (rows == null || !rows.isArray() || rows.size() == 0) {
            return null;
        }
        JsonNode row = rows.get(0);
        String shortName = text(row, "c");
        String fullName = text(row, "n");
        String name = shortName != null ? shortName : fullName;
        if (name == null || name.isBlank()) {
            return null;
        }
        return new OneCInnRequisites(name, fullName != null ? fullName : name, text(row, "i"), text(row, "p"));
    }

    public record CreatedDocument(String refKey, String number, LocalDate date) {
    }

    public record PaymentOrderState(
            String refKey,
            String number,
            LocalDate date,
            boolean posted,
            boolean deletionMark,
            boolean paid,
            String bankState) {
        public PaymentOrderState withPaid(boolean paidFlag) {
            return new PaymentOrderState(refKey, number, date, posted, deletionMark, paidFlag, bankState);
        }

        public PaymentOrderState withBankState(String state) {
            return new PaymentOrderState(refKey, number, date, posted, deletionMark, paid, state);
        }

        public boolean prepared() {
            return isPreparedBankState(bankState);
        }

        public boolean blocksReturn() {
            return paid || isLockedBankState(bankState);
        }
    }

    private static List<JsonNode> rows(String body) {
        JsonNode root = JSON.readTree(body);
        List<JsonNode> result = new ArrayList<>();
        JsonNode value = root.get("value");
        if (value != null && value.isArray()) {
            for (JsonNode row : value) {
                result.add(row);
            }
            return result;
        }
        JsonNode d = root.get("d");
        if (d != null) {
            JsonNode results = d.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode row : results) {
                    result.add(row);
                }
                return result;
            }
        }
        if (root.get("Ref_Key") != null || root.get("Договор_Key") != null || root.get("Number") != null) {
            result.add(root);
        }
        return result;
    }

    private static JsonNode firstRow(JsonNode root) {
        JsonNode value = root.get("value");
        if (value != null && value.isArray() && value.size() > 0) {
            return value.get(0);
        }
        JsonNode d = root.get("d");
        if (d != null) {
            JsonNode results = d.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                return results.get(0);
            }
        }
        if (root.get("Ref_Key") != null || root.get("Number") != null) {
            return root;
        }
        return null;
    }

    private static LocalDate localDate(JsonNode row, String... names) {
        for (String name : names) {
            JsonNode node = row.get(name);
            if (node == null || node.isNull()) {
                continue;
            }
            String raw = node.asString();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            raw = raw.trim();
            if (raw.startsWith("/Date(") && raw.endsWith(")/")) {
                String inner = raw.substring(6, raw.length() - 2);
                int sign = inner.indexOf('+');
                if (sign < 0) {
                    sign = inner.indexOf('-', 1);
                }
                if (sign > 0) {
                    inner = inner.substring(0, sign);
                }
                try {
                    long millis = Long.parseLong(inner);
                    return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
            if (raw.length() >= 10) {
                try {
                    return LocalDate.parse(raw.substring(0, 10));
                } catch (DateTimeParseException ignored) {
                    continue;
                }
            }
        }
        return null;
    }

    private static UUID uuid(JsonNode row, String... names) {
        for (String name : names) {
            UUID parsed = uuidNode(row.get(name));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static UUID uuidNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            UUID nested = uuidNode(node.get("Ref_Key"));
            if (nested != null) {
                return nested;
            }
            nested = uuidNode(node.get("Ref"));
            if (nested != null) {
                return nested;
            }
            JsonNode deferred = node.get("__deferred");
            if (deferred != null && deferred.isObject()) {
                return uuidFromDeferredUri(deferred.get("uri"));
            }
            return null;
        }
        String raw = node.asString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        if (raw.startsWith("guid'") && raw.endsWith("'") && raw.length() > 6) {
            raw = raw.substring(5, raw.length() - 1);
        }
        if ("00000000-0000-0000-0000-000000000000".equals(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID uuidFromDeferredUri(JsonNode uriNode) {
        if (uriNode == null || uriNode.isNull()) {
            return null;
        }
        String uri = uriNode.asString();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        int guidStart = uri.indexOf("guid'");
        if (guidStart < 0) {
            return null;
        }
        int guidEnd = uri.indexOf('\'', guidStart + 5);
        if (guidEnd <= guidStart + 5) {
            return null;
        }
        try {
            String raw = uri.substring(guidStart + 5, guidEnd);
            if ("00000000-0000-0000-0000-000000000000".equals(raw)) {
                return null;
            }
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static OneCInnRequisites parseLegalRequisites(JsonNode legal) {
        JsonNode nameNode = legal.get("СвНаимЮЛ");
        String fullName = attr(nameNode, "НаимЮЛПолн");
        JsonNode shortNode = nameNode == null ? null : nameNode.get("СвНаимЮЛСокр");
        String name = attr(shortNode, "НаимСокр");
        if (name == null) {
            name = attr(nameNode, "НаимЮЛСокр");
        }
        if (name == null) {
            name = fullName;
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        return new OneCInnRequisites(name, fullName, attr(legal, "ИНН"), attr(legal, "КПП"));
    }

    private static OneCInnRequisites parseEntrepreneurRequisites(JsonNode entrepreneur) {
        JsonNode person = entrepreneur.get("СвФЛ");
        String lastName = attr(person, "Фамилия");
        String firstName = attr(person, "Имя");
        String middleName = attr(person, "Отчество");
        StringBuilder fio = new StringBuilder();
        appendPart(fio, lastName);
        appendPart(fio, firstName);
        appendPart(fio, middleName);
        if (fio.isEmpty()) {
            return null;
        }
        return new OneCInnRequisites(
                "ИП " + fio,
                "ИНДИВИДУАЛЬНЫЙ ПРЕДПРИНИМАТЕЛЬ " + fio,
                attr(entrepreneur, "ИНН"),
                null);
    }

    private static void appendPart(StringBuilder target, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!target.isEmpty()) {
            target.append(' ');
        }
        target.append(part.trim());
    }

    private static String attr(JsonNode node, String name) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode attributes = node.get("@attributes");
        if (attributes != null && attributes.isObject()) {
            String value = text(attributes, name);
            if (value != null) {
                return value;
            }
        }
        return text(node, name);
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

    private static boolean bool(JsonNode row, String name) {
        JsonNode node = row.get(name);
        return node != null && node.isBoolean() && node.asBoolean();
    }
}

package com.company.money.onec;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OneCCatalogClient {

    static final String COUNTERPARTIES = "Catalog_Контрагенты";
    static final String NOMENCLATURE = "Catalog_Номенклатура";
    static final String BANK_ACCOUNTS = "Catalog_БанковскиеСчета";
    static final String CONTRACTS = "Catalog_ДоговорыКонтрагентов";
    static final String MAIN_CONTRACTS = "InformationRegister_ОсновныеДоговорыКонтрагента";
    static final String EMPLOYEES = "Catalog_Сотрудники";
    static final String BANK_ACCOUNT_SELECT =
            "Ref_Key,Description,Code,Owner,Owner_Type,НомерСчета,DeletionMark";
    static final String CONTRACT_SELECT =
            "Ref_Key,Description,Code,Owner,Owner_Key,ВидДоговора,СтавкаНДС,СуммаВключаетНДС,"
                    + "ВалютаВзаиморасчетов_Key,DeletionMark";
    private static final String SUPPLIER_KIND = "СПоставщиком";
    private static final int BANK_ACCOUNT_PAGE_SIZE = 200;
    private static final int BANK_ACCOUNT_SCAN_LIMIT = 10000;

    private static final String NALOG_SEARCH_URL = "https://egrul.nalog.ru/";
    private static final String NALOG_RESULT_URL = "https://egrul.nalog.ru/search-result/{token}";

    private final OneCOdataClient odataClient;
    private final RestClient egrulClient;

    public OneCCatalogClient(OneCOdataClient odataClient) {
        this.odataClient = odataClient;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.egrulClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .build();
    }

    public String getOrganizationKey() {
        return odataClient.getOrganizationKey();
    }

    public List<OneCCatalogItem> searchCounterparties(String text, int skip, int top) {
        return search(COUNTERPARTIES, text, skip, top, "ИНН", "Code");
    }

    public List<OneCCatalogItem> searchNomenclature(String text, int skip, int top) {
        return search(NOMENCLATURE, text, skip, top, "Артикул", "Code");
    }

    public List<OneCBankAccountItem> searchBankAccounts(UUID ownerRef, String text, int skip, int top) {
        int needed = Math.max(skip, 0) + Math.max(top, 1);
        List<OneCBankAccountItem> matched = new ArrayList<>();
        int odataSkip = 0;
        while (matched.size() < needed && odataSkip < BANK_ACCOUNT_SCAN_LIMIT) {
            String body = odataClient.get(
                    BANK_ACCOUNTS,
                    bankAccountFilter(text),
                    odataSkip,
                    BANK_ACCOUNT_PAGE_SIZE,
                    "Description",
                    BANK_ACCOUNT_SELECT);
            List<OneCBankAccountItem> page = OneCOdataParser.parseBankAccounts(body == null ? "{}" : body);
            if (page.isEmpty()) {
                break;
            }
            for (OneCBankAccountItem item : page) {
                if (ownerRef == null || ownerRef.equals(item.ownerRef())) {
                    matched.add(item);
                }
            }
            if (page.size() < BANK_ACCOUNT_PAGE_SIZE) {
                break;
            }
            odataSkip += BANK_ACCOUNT_PAGE_SIZE;
        }
        return matched.stream()
                .skip(Math.max(skip, 0))
                .limit(Math.max(top, 1))
                .toList();
    }

    public List<OneCContractItem> searchContracts(UUID ownerRef, String text, int skip, int top) {
        if (ownerRef == null) {
            return List.of();
        }
        String body = odataClient.get(
                CONTRACTS,
                contractFilter(ownerRef, text),
                skip,
                top,
                "Description",
                CONTRACT_SELECT);
        return OneCOdataParser.parseContracts(body == null ? "{}" : body);
    }

    public OneCContractItem findMainContract(UUID ownerRef, String organizationKey) {
        if (ownerRef == null) {
            return null;
        }
        UUID contractRef = findMainContractRef(ownerRef, organizationKey);
        if (contractRef != null) {
            OneCContractItem byKey = loadContract(contractRef);
            if (byKey != null) {
                return byKey;
            }
        }
        List<OneCContractItem> contracts = searchContracts(ownerRef, "", 0, 50);
        for (OneCContractItem item : contracts) {
            if (SUPPLIER_KIND.equals(item.contractKind())) {
                return item;
            }
        }
        return contracts.isEmpty() ? null : contracts.getFirst();
    }

    private UUID findMainContractRef(UUID ownerRef, String organizationKey) {
        try {
            String filter = mainContractFilter(ownerRef, organizationKey);
            String body = odataClient.get(MAIN_CONTRACTS, filter, 0, 5, null,
                    "Договор_Key,Договор,Организация_Key,Контрагент_Key,ВидДоговора");
            return OneCOdataParser.parseMainContractRef(body == null ? "{}" : body);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private OneCContractItem loadContract(UUID contractRef) {
        try {
            String body = odataClient.getByKey(CONTRACTS, contractRef.toString(), CONTRACT_SELECT);
            List<OneCContractItem> items = OneCOdataParser.parseContracts(body == null ? "{}" : body);
            return items.isEmpty() ? null : items.getFirst();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public List<OneCCatalogItem> searchEmployees(String text, int skip, int top) {
        String body = odataClient.get(EMPLOYEES, employeeFilter(text), skip, top, "Description");
        return OneCOdataParser.parse(body == null ? "{}" : body);
    }

    public List<OneCCatalogItem> findCounterpartiesByInn(String inn) {
        if (inn == null || inn.isBlank()) {
            return List.of();
        }
        String escaped = inn.trim().replace("'", "''");
        String filter = "DeletionMark eq false and IsFolder eq false and ИНН eq '" + escaped + "'";
        String body = odataClient.get(COUNTERPARTIES, filter, 0, 20, "Description");
        return OneCOdataParser.parse(body == null ? "{}" : body);
    }

    public OneCInnRequisites lookupRequisitesByInn(String inn) {
        if (inn == null || inn.isBlank()) {
            return null;
        }
        try {
            String tokenBody = egrulClient.post()
                    .uri(NALOG_SEARCH_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("query=" + inn)
                    .retrieve()
                    .body(String.class);
            String token = OneCOdataParser.parseNalogSearchToken(tokenBody);
            if (token == null) {
                return null;
            }
            String resultBody = egrulClient.get()
                    .uri(NALOG_RESULT_URL, token)
                    .retrieve()
                    .body(String.class);
            return OneCOdataParser.parseNalogSearchResult(resultBody);
        } catch (RestClientException ignored) {
            return null;
        }
    }

    private List<OneCCatalogItem> search(String entitySet, String text, int skip, int top,
                                         String extraField1, String extraField2) {
        String body = odataClient.get(entitySet, odataFilter(text, extraField1, extraField2), skip, top, "Description");
        return OneCOdataParser.parse(body == null ? "{}" : body);
    }

    static String odataFilter(String text, String extraField1, String extraField2) {
        StringBuilder filter = new StringBuilder("DeletionMark eq false and IsFolder eq false");
        if (text != null && !text.isBlank()) {
            String escaped = text.trim().replace("'", "''");
            filter.append(" and (substringof('")
                    .append(escaped)
                    .append("',Description) eq true or substringof('")
                    .append(escaped)
                    .append("',")
                    .append(extraField1)
                    .append(") eq true or substringof('")
                    .append(escaped)
                    .append("',")
                    .append(extraField2)
                    .append(") eq true)");
        }
        return filter.toString();
    }

    static String bankAccountFilter(String text) {
        StringBuilder filter = new StringBuilder(
                "DeletionMark eq false and Owner_Type eq 'StandardODATA.Catalog_Контрагенты'");
        appendTextSearch(filter, text, "НомерСчета", "Code");
        return filter.toString();
    }

    static String contractFilter(UUID ownerRef, String text) {
        StringBuilder filter = new StringBuilder("DeletionMark eq false and Owner_Key eq guid'")
                .append(ownerRef)
                .append("'");
        appendTextSearch(filter, text, "Code");
        return filter.toString();
    }

    static String mainContractFilter(UUID ownerRef, String organizationKey) {
        StringBuilder filter = new StringBuilder("Контрагент_Key eq guid'")
                .append(ownerRef)
                .append("' and ВидДоговора eq '")
                .append(SUPPLIER_KIND)
                .append("'");
        if (organizationKey != null && !organizationKey.isBlank()) {
            filter.append(" and Организация_Key eq guid'").append(organizationKey).append("'");
        }
        return filter.toString();
    }

    static String employeeFilter(String text) {
        StringBuilder filter = new StringBuilder("DeletionMark eq false and ВАрхиве eq false");
        appendTextSearch(filter, text, "Code");
        return filter.toString();
    }

    private static void appendTextSearch(StringBuilder filter, String text, String... extraFields) {
        if (text == null || text.isBlank()) {
            return;
        }
        String escaped = text.trim().replace("'", "''");
        filter.append(" and (substringof('").append(escaped).append("',Description) eq true");
        for (String extraField : extraFields) {
            filter.append(" or substringof('").append(escaped).append("',").append(extraField).append(") eq true");
        }
        filter.append(")");
    }
}

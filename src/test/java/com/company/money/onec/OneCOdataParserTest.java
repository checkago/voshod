package com.company.money.onec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OneCOdataParserTest {

    @Test
    void parsesOdataValueArray() {
        String body = """
                {"value":[
                  {"Ref_Key":"11111111-1111-1111-1111-111111111111",
                   "Description":"ООО Ромашка","Code":"000000001",
                   "ИНН":"7701234567","КПП":"770101001",
                   "IsFolder":false,"DeletionMark":false},
                  {"Ref_Key":"22222222-2222-2222-2222-222222222222",
                   "Description":"Группа","IsFolder":true,"DeletionMark":false}
                ]}
                """;

        List<OneCCatalogItem> items = OneCOdataParser.parse(body);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().oneCRef())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(items.getFirst().name()).isEqualTo("ООО Ромашка");
        assertThat(items.getFirst().inn()).isEqualTo("7701234567");
    }

    @Test
    void parsesRefKeysFromOdataValueArray() {
        assertThat(OneCOdataParser.parseRefKeys("""
                {"value":[
                  {"Ref_Key":"dddddddd-dddd-dddd-dddd-dddddddddddd","DeletionMark":false},
                  {"Ref_Key":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"}
                ]}
                """)).containsExactly(
                "dddddddd-dddd-dddd-dddd-dddddddddddd",
                "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        assertThat(OneCOdataParser.parseRefKeys(null)).isEmpty();
        assertThat(OneCOdataParser.parseRefKeys("<html><title>Not Found</title></html>")).isEmpty();
    }

    @Test
    void parsesCreatedPaymentOrder() {
        OneCOdataParser.CreatedDocument created = OneCOdataParser.parseCreatedDocument("""
                {"Ref_Key":"0e975c5a-b5c5-11f1-86b8-bc2411b26e14","Number":"00БП-002513","Date":"2026-09-22T00:00:00"}
                """);

        assertThat(created.refKey()).isEqualTo("0e975c5a-b5c5-11f1-86b8-bc2411b26e14");
        assertThat(created.number()).isEqualTo("00БП-002513");
        assertThat(created.date()).isEqualTo(java.time.LocalDate.of(2026, 9, 22));
    }

    @Test
    void parsesPaymentOrderPostedFlag() {
        OneCOdataParser.PaymentOrderState state = OneCOdataParser.parsePaymentOrder("""
                {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Number":"00БП-000001",
                 "Date":"2026-09-22T00:00:00","Posted":true,"DeletionMark":false}
                """);
        assertThat(state.posted()).isTrue();
        assertThat(state.paid()).isFalse();
        assertThat(state.number()).isEqualTo("00БП-000001");
        assertThat(state.date()).isEqualTo(java.time.LocalDate.of(2026, 9, 22));
    }

    @Test
    void postedPaymentOrderIsNotPaid() {
        assertThat(OneCOdataParser.parsePaidBankState("""
                {"value":[{"Состояние":"Подготовлено"}]}
                """)).isFalse();
        assertThat(OneCOdataParser.parsePaidBankState("""
                {"value":[{"Состояние":"Отправлено"}]}
                """)).isFalse();
        assertThat(OneCOdataParser.parsePaidBankState("""
                {"value":[{"Состояние":"Оплачено"}]}
                """)).isTrue();
        assertThat(OneCOdataParser.parseBankDocumentState("""
                {"value":[{"Состояние":"Подготовлено"}]}
                """)).isEqualTo("Подготовлено");
        assertThat(OneCOdataParser.parseBankDocumentState("""
                {"value":[{"Состояние":"Отправлено"}]}
                """)).isEqualTo("Отправлено");
        assertThat(OneCOdataParser.isPreparedBankState("Подготовлено")).isTrue();
        assertThat(OneCOdataParser.isPreparedBankState("Отправлено")).isFalse();
        assertThat(OneCOdataParser.isLockedBankState("Отправлено")).isTrue();
        assertThat(OneCOdataParser.isLockedBankState("Оплачено")).isTrue();
        assertThat(OneCOdataParser.isLockedBankState("Подготовлено")).isFalse();
        assertThat(OneCOdataParser.isLockedBankState("НаПодписи")).isFalse();
        assertThat(OneCOdataParser.parsePaymentOrder("""
                {"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","Posted":false}
                """).blocksReturn()).isFalse();
        assertThat(OneCOdataParser.parsePostedWriteOff("""
                {"value":[{"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "Posted":true,"DeletionMark":false}]}
                """)).isTrue();
        assertThat(OneCOdataParser.parsePostedWriteOff("""
                {"value":[{"Ref_Key":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "Posted":false,"DeletionMark":false}]}
                """)).isFalse();
    }

    @Test
    void parsesContractsAndMainContractRef() {
        List<OneCContractItem> items = OneCOdataParser.parseContracts("""
                {"value":[{"Ref_Key":"44444444-4444-4444-4444-444444444444",
                  "Description":"Основной","Code":"000000001",
                  "Owner_Key":"11111111-1111-1111-1111-111111111111",
                  "ВидДоговора":"СПоставщиком","СтавкаНДС":"НДС20",
                  "СуммаВключаетНДС":true,"DeletionMark":false}]}
                """);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().vatRate()).isEqualTo("НДС20");
        assertThat(items.getFirst().amountIncludesVat()).isTrue();
        assertThat(OneCOdataParser.parseMainContractRef("""
                {"value":[{"Договор_Key":"44444444-4444-4444-4444-444444444444"}]}
                """)).isEqualTo(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void buildsFilterForTypedSearch() {
        assertThat(OneCCatalogClient.odataFilter("рома", "ИНН", "Code"))
                .contains("DeletionMark eq false")
                .contains("substringof('рома',Description)")
                .contains("substringof('рома',ИНН)")
                .contains("substringof('рома',Code)");
        assertThat(OneCCatalogClient.bankAccountFilter("40702"))
                .contains("DeletionMark eq false")
                .contains("Owner_Type eq 'StandardODATA.Catalog_Контрагенты'")
                .contains("substringof('40702',НомерСчета)")
                .doesNotContain("IsFolder");
        assertThat(OneCCatalogClient.employeeFilter("иван"))
                .contains("ВАрхиве eq false")
                .contains("substringof('иван',Description)")
                .contains("substringof('иван',Code)")
                .doesNotContain("IsFolder");
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThat(OneCCatalogClient.contractFilter(owner, "осн"))
                .contains("Owner_Key eq guid'" + owner + "'")
                .contains("substringof('осн',Description)");
        assertThat(OneCCatalogClient.mainContractFilter(owner, "c1405bb8-2942-11e5-874a-001e101f4da1"))
                .contains("Контрагент_Key eq guid'" + owner + "'")
                .contains("ВидДоговора eq 'СПоставщиком'")
                .contains("Организация_Key eq guid'c1405bb8-2942-11e5-874a-001e101f4da1'");
    }

    @Test
    void parsesBankAccountsAndOwnerKey() {
        String body = """
                {"value":[
                  {"Ref_Key":"33333333-3333-3333-3333-333333333333",
                   "Description":"Расчётный","Code":"000000001",
                   "НомерСчета":"40702810100000000001",
                   "Owner_Key":"11111111-1111-1111-1111-111111111111",
                   "DeletionMark":false}
                ]}
                """;

        List<OneCBankAccountItem> items = OneCOdataParser.parseBankAccounts(body);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().accountNumber()).isEqualTo("40702810100000000001");
        assertThat(items.getFirst().ownerRef())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void parsesBankAccountOwnerFromGuidLiteral() {
        String body = """
                {"value":[
                  {"Ref_Key":"33333333-3333-3333-3333-333333333333",
                   "Description":"Расчётный",
                   "Owner":"guid'11111111-1111-1111-1111-111111111111'",
                   "DeletionMark":false}
                ]}
                """;

        List<OneCBankAccountItem> items = OneCOdataParser.parseBankAccounts(body);

        assertThat(items.getFirst().ownerRef())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void parsesBankAccountNameFromAccountNumberWhenDescriptionEmpty() {
        String body = """
                {"value":[
                  {"Ref_Key":"33333333-3333-3333-3333-333333333333",
                   "Description":"",
                   "НомерСчета":"40702810100000000001",
                   "Owner":"11111111-1111-1111-1111-111111111111",
                   "DeletionMark":false}
                ]}
                """;

        List<OneCBankAccountItem> items = OneCOdataParser.parseBankAccounts(body);

        assertThat(items.getFirst().name()).isEqualTo("40702810100000000001");
        assertThat(items.getFirst().accountNumber()).isEqualTo("40702810100000000001");
    }

    @Test
    void parsesBankAccountOwnerFromDeferredUri() {
        String body = """
                {"value":[
                  {"Ref_Key":"33333333-3333-3333-3333-333333333333",
                   "Description":"Расчётный",
                   "Owner":{"__deferred":{"uri":"Catalog_Контрагенты(guid'11111111-1111-1111-1111-111111111111')"}},
                   "DeletionMark":false}
                ]}
                """;

        List<OneCBankAccountItem> items = OneCOdataParser.parseBankAccounts(body);

        assertThat(items.getFirst().ownerRef())
                .isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void parsesOdataErrorMessage() {
        assertThat(OneCOdataParser.parseError("""
                {"odata.error":{"code":"4","message":{"lang":"ru","value":"СчетКонтрагента"}}}
                """)).isEqualTo("СчетКонтрагента");
        assertThat(OneCOdataParser.parseError("""
                <html><head><title>Not Found</title></head><body>404</body></html>
                """)).isEqualTo("Not Found");
        assertThat(OneCOdataParser.parseError("<html><body>oops</body></html>")).isNull();
    }

    @Test
    void parsesEgrulLegalRequisites() {
        OneCInnRequisites requisites = OneCOdataParser.parseEgrulRequisites("""
                {"СвЮЛ":{"@attributes":{"ИНН":"7707083893","КПП":"773601001"},
                 "СвНаимЮЛ":{"@attributes":{"НаимЮЛПолн":"ПУБЛИЧНОЕ АКЦИОНЕРНОЕ ОБЩЕСТВО \\"СБЕРБАНК РОССИИ\\""},
                  "СвНаимЮЛСокр":{"@attributes":{"НаимСокр":"ПАО СБЕРБАНК"}}}}}
                """);

        assertThat(requisites).isNotNull();
        assertThat(requisites.name()).isEqualTo("ПАО СБЕРБАНК");
        assertThat(requisites.fullName()).isEqualTo("ПУБЛИЧНОЕ АКЦИОНЕРНОЕ ОБЩЕСТВО \"СБЕРБАНК РОССИИ\"");
        assertThat(requisites.inn()).isEqualTo("7707083893");
        assertThat(requisites.kpp()).isEqualTo("773601001");
    }

    @Test
    void parsesNalogLegalSearchResult() {
        OneCInnRequisites requisites = OneCOdataParser.parseNalogSearchResult("""
                {"rows":[{"c":"ПАО СБЕРБАНК","i":"7707083893","k":"ul",
                 "n":"ПУБЛИЧНОЕ АКЦИОНЕРНОЕ ОБЩЕСТВО \\"СБЕРБАНК РОССИИ\\"","p":"773601001"}]}
                """);

        assertThat(requisites).isNotNull();
        assertThat(requisites.name()).isEqualTo("ПАО СБЕРБАНК");
        assertThat(requisites.fullName()).isEqualTo("ПУБЛИЧНОЕ АКЦИОНЕРНОЕ ОБЩЕСТВО \"СБЕРБАНК РОССИИ\"");
        assertThat(requisites.inn()).isEqualTo("7707083893");
        assertThat(requisites.kpp()).isEqualTo("773601001");
    }

    @Test
    void parsesNalogEntrepreneurSearchResult() {
        OneCInnRequisites requisites = OneCOdataParser.parseNalogSearchResult("""
                {"rows":[{"c":"ИП ИВАНОВ ИВАН ИВАНОВИЧ","i":"773605000003","k":"fl",
                 "n":"ИНДИВИДУАЛЬНЫЙ ПРЕДПРИНИМАТЕЛЬ ИВАНОВ ИВАН ИВАНОВИЧ"}]}
                """);

        assertThat(requisites).isNotNull();
        assertThat(requisites.name()).isEqualTo("ИП ИВАНОВ ИВАН ИВАНОВИЧ");
        assertThat(requisites.fullName()).isEqualTo("ИНДИВИДУАЛЬНЫЙ ПРЕДПРИНИМАТЕЛЬ ИВАНОВ ИВАН ИВАНОВИЧ");
        assertThat(requisites.inn()).isEqualTo("773605000003");
    }

    @Test
    void parseNalogSearchTokenRejectsCaptcha() {
        assertThat(OneCOdataParser.parseNalogSearchToken("""
                {"t":"abc","captchaRequired":true}
                """)).isNull();
        assertThat(OneCOdataParser.parseNalogSearchToken("""
                {"t":"abc","captchaRequired":false}
                """)).isEqualTo("abc");
    }

    @Test
    void parsesEgrulEntrepreneurRequisites() {
        OneCInnRequisites requisites = OneCOdataParser.parseEgrulRequisites("""
                {"СвИП":{"@attributes":{"ИНН":"770123456789"},
                 "СвФЛ":{"@attributes":{"Фамилия":"Иванов","Имя":"Иван","Отчество":"Иванович"}}}}
                """);

        assertThat(requisites).isNotNull();
        assertThat(requisites.name()).isEqualTo("ИП Иванов Иван Иванович");
        assertThat(requisites.fullName()).isEqualTo("ИНДИВИДУАЛЬНЫЙ ПРЕДПРИНИМАТЕЛЬ Иванов Иван Иванович");
        assertThat(requisites.inn()).isEqualTo("770123456789");
    }
}

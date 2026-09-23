package com.company.money.onec;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import io.jmix.core.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class OneCCatalogSearchServiceTest {

    @Autowired
    OneCCatalogSearchService oneCCatalogSearchService;
    @Autowired
    DataManager dataManager;
    @MockitoBean
    OneCCatalogClient oneCCatalogClient;

    private final List<Object> cleanup = new ArrayList<>();

    @Test
    void upsertsCounterpartyByOneCRef() {
        UUID oneCRef = UUID.randomUUID();
        when(oneCCatalogClient.searchCounterparties(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new OneCCatalogItem(
                        oneCRef, "000000001", "ООО Тест", "Общество Тест",
                        "7701234567", "770101001", null, false)));

        Counterparty first = oneCCatalogSearchService.searchCounterparties("тест", 0, 20).getFirst();
        cleanup.add(first);
        Counterparty second = oneCCatalogSearchService.searchCounterparties("тест", 0, 20).getFirst();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getOneCRef()).isEqualTo(oneCRef);
        assertThat(second.getName()).isEqualTo("ООО Тест");
        assertThat(second.getInn()).isEqualTo("7701234567");
    }

    @Test
    void upsertsBankAccountByOneCRef() {
        UUID counterpartyRef = UUID.randomUUID();
        UUID accountRef = UUID.randomUUID();
        when(oneCCatalogClient.searchCounterparties(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new OneCCatalogItem(
                        counterpartyRef, "000000001", "ООО Тест", "Общество Тест",
                        "7701234567", "770101001", null, false)));
        Counterparty counterparty = oneCCatalogSearchService.searchCounterparties("тест", 0, 20).getFirst();
        cleanup.add(counterparty);

        when(oneCCatalogClient.searchBankAccounts(eq(counterpartyRef), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new OneCBankAccountItem(
                        accountRef, "000000001", "Расчётный", "40702810100000000001", counterpartyRef)));

        CounterpartyBankAccount first = oneCCatalogSearchService
                .searchBankAccounts(counterparty, "", 0, 20).getFirst();
        cleanup.add(first);
        CounterpartyBankAccount second = oneCCatalogSearchService
                .searchBankAccounts(counterparty, "", 0, 20).getFirst();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getOneCRef()).isEqualTo(accountRef);
        assertThat(second.getAccountNumber()).isEqualTo("40702810100000000001");
        assertThat(second.getCounterparty().getId()).isEqualTo(counterparty.getId());
    }

    @Test
    void upsertsContractByOneCRef() {
        UUID counterpartyRef = UUID.randomUUID();
        UUID contractRef = UUID.randomUUID();
        when(oneCCatalogClient.searchCounterparties(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new OneCCatalogItem(
                        counterpartyRef, "000000001", "ООО Тест", "Общество Тест",
                        "7701234567", "770101001", null, false)));
        Counterparty counterparty = oneCCatalogSearchService.searchCounterparties("тест", 0, 20).getFirst();
        cleanup.add(counterparty);

        when(oneCCatalogClient.searchContracts(eq(counterpartyRef), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new OneCContractItem(
                        contractRef, "000000001", "Основной", counterpartyRef,
                        "СПоставщиком", "НДС20", true, null)));

        com.company.money.entity.CounterpartyContract first = oneCCatalogSearchService
                .searchContracts(counterparty, "", 0, 20).getFirst();
        cleanup.add(first);
        com.company.money.entity.CounterpartyContract second = oneCCatalogSearchService
                .searchContracts(counterparty, "", 0, 20).getFirst();

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getOneCRef()).isEqualTo(contractRef);
        assertThat(second.getVatRate()).isEqualTo("НДС20");
        assertThat(second.getAmountIncludesVat()).isTrue();
    }

    @Test
    void createsPendingCounterpartyWhenInnNotInOneC() {
        when(oneCCatalogClient.findCounterpartiesByInn("9900112266")).thenReturn(List.of());
        when(oneCCatalogClient.searchCounterparties("9900112266", 0, 20)).thenReturn(List.of());
        when(oneCCatalogClient.lookupRequisitesByInn("9900112266"))
                .thenReturn(new OneCInnRequisites(
                        "ООО По ИНН", "Общество По ИНН", "9900112266", "990101001"));

        OneCCatalogSearchService.InnResolveResult resolved =
                oneCCatalogSearchService.resolveByInn("9900112266");
        cleanup.add(resolved.counterparty());

        assertThat(resolved.foundInOneC()).isFalse();
        assertThat(resolved.counterparty().getInn()).isEqualTo("9900112266");
        assertThat(resolved.counterparty().getName()).isEqualTo("ООО По ИНН");
        assertThat(resolved.counterparty().getFullName()).isEqualTo("Общество По ИНН");
        assertThat(resolved.counterparty().getKpp()).isEqualTo("990101001");
    }

    @Test
    void resolveByInnFailsWhenRequisitesUnavailable() {
        when(oneCCatalogClient.findCounterpartiesByInn("9900112266")).thenReturn(List.of());
        when(oneCCatalogClient.searchCounterparties("9900112266", 0, 20)).thenReturn(List.of());
        when(oneCCatalogClient.lookupRequisitesByInn("9900112266")).thenReturn(null);

        assertThatThrownBy(() -> oneCCatalogSearchService.resolveByInn("9900112266"))
                .isInstanceOf(com.company.money.service.PaymentRequestException.class);
    }

    @Test
    void resolveByInnUpsertsExistingOneCCounterparty() {
        UUID oneCRef = UUID.randomUUID();
        when(oneCCatalogClient.findCounterpartiesByInn("7701234567"))
                .thenReturn(List.of(new OneCCatalogItem(
                        oneCRef, "000000001", "ООО Найден", "Общество Найден",
                        "7701234567", "770101001", null, false)));

        OneCCatalogSearchService.InnResolveResult resolved =
                oneCCatalogSearchService.resolveByInn("7701234567");
        cleanup.add(resolved.counterparty());

        assertThat(resolved.foundInOneC()).isTrue();
        assertThat(resolved.counterparty().getOneCRef()).isEqualTo(oneCRef);
        assertThat(resolved.counterparty().getName()).isEqualTo("ООО Найден");
    }

    @AfterEach
    void tearDown() {
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            Object entity = cleanup.get(i);
            dataManager.load(Id.of(entity)).optional().ifPresent(dataManager::remove);
        }
        cleanup.clear();
    }
}

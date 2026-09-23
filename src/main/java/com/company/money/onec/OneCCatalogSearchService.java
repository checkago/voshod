package com.company.money.onec;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.Nomenclature;
import com.company.money.service.PaymentRequestException;
import io.jmix.core.DataManager;
import io.jmix.core.Messages;
import io.jmix.core.UnconstrainedDataManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OneCCatalogSearchService {

    private final OneCCatalogClient oneCCatalogClient;
    private final UnconstrainedDataManager dataManager;
    private final Messages messages;

    public OneCCatalogSearchService(OneCCatalogClient oneCCatalogClient,
                                    DataManager dataManager,
                                    Messages messages) {
        this.oneCCatalogClient = oneCCatalogClient;
        this.dataManager = dataManager.unconstrained();
        this.messages = messages;
    }

    @Transactional
    public List<Counterparty> searchCounterparties(String text, int skip, int top) {
        List<Counterparty> result = new ArrayList<>();
        for (OneCCatalogItem item : oneCCatalogClient.searchCounterparties(text, skip, top)) {
            result.add(upsertCounterparty(item));
        }
        return result;
    }

    @Transactional
    public List<Nomenclature> searchNomenclature(String text, int skip, int top) {
        List<Nomenclature> result = new ArrayList<>();
        for (OneCCatalogItem item : oneCCatalogClient.searchNomenclature(text, skip, top)) {
            result.add(upsertNomenclature(item));
        }
        return result;
    }

    @Transactional
    public List<CounterpartyBankAccount> searchBankAccounts(Counterparty counterparty, String text, int skip, int top) {
        if (counterparty == null || counterparty.getOneCRef() == null) {
            return List.of();
        }
        List<CounterpartyBankAccount> result = new ArrayList<>();
        for (OneCBankAccountItem item : oneCCatalogClient.searchBankAccounts(
                counterparty.getOneCRef(), text, skip, top)) {
            result.add(upsertBankAccount(counterparty, item));
        }
        return result;
    }

    @Transactional
    public List<CounterpartyContract> searchContracts(Counterparty counterparty, String text, int skip, int top) {
        if (counterparty == null || counterparty.getOneCRef() == null) {
            return List.of();
        }
        List<CounterpartyContract> result = new ArrayList<>();
        for (OneCContractItem item : oneCCatalogClient.searchContracts(
                counterparty.getOneCRef(), text, skip, top)) {
            result.add(upsertContract(counterparty, item));
        }
        return result;
    }

    @Transactional
    public CounterpartyContract findMainContract(Counterparty counterparty) {
        if (counterparty == null || counterparty.getOneCRef() == null) {
            return null;
        }
        OneCContractItem item = oneCCatalogClient.findMainContract(
                counterparty.getOneCRef(), oneCCatalogClient.getOrganizationKey());
        if (item == null) {
            return null;
        }
        return upsertContract(counterparty, item);
    }

    public List<OneCCatalogItem> searchEmployees(String text, int skip, int top) {
        return oneCCatalogClient.searchEmployees(text, skip, top);
    }

    @Transactional
    public InnResolveResult resolveByInn(String inn) {
        String normalized = normalizeInn(inn);
        requireValidInn(normalized);
        List<OneCCatalogItem> items = oneCCatalogClient.findCounterpartiesByInn(normalized);
        if (items.isEmpty()) {
            items = oneCCatalogClient.searchCounterparties(normalized, 0, 20).stream()
                    .filter(item -> normalized.equals(item.inn()))
                    .toList();
        }
        if (!items.isEmpty()) {
            return new InnResolveResult(upsertCounterparty(items.getFirst()), true);
        }
        OneCInnRequisites requisites = oneCCatalogClient.lookupRequisitesByInn(normalized);
        if (requisites == null || requisites.name() == null || requisites.name().isBlank()) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/paymentRequest.error.cannotFillByInn"));
        }
        return new InnResolveResult(createPendingCounterparty(normalized, requisites), false);
    }

    @Transactional
    public CounterpartyLinks loadLinksFromOneC(String inn) {
        InnResolveResult resolved = resolveByInn(inn);
        if (!resolved.foundInOneC()) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/paymentRequest.error.counterpartyNotInOneC"));
        }
        Counterparty counterparty = resolved.counterparty();
        CounterpartyBankAccount bank = searchBankAccounts(counterparty, "", 0, 1)
                .stream()
                .findFirst()
                .orElse(null);
        CounterpartyContract contract = findMainContract(counterparty);
        return new CounterpartyLinks(counterparty, bank, contract);
    }

    public static String normalizeInn(String inn) {
        if (inn == null) {
            return "";
        }
        return inn.replaceAll("\\D", "");
    }

    public static boolean isValidInn(String inn) {
        return inn.length() == 10 || inn.length() == 12;
    }

    private void requireValidInn(String inn) {
        if (inn.isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needInn"));
        }
        if (!isValidInn(inn)) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.invalidInn"));
        }
    }

    private Counterparty createPendingCounterparty(String inn, OneCInnRequisites requisites) {
        Counterparty existing = dataManager.load(Counterparty.class)
                .query("select e from vshd1_Counterparty e where e.inn = :inn order by e.name")
                .parameter("inn", inn)
                .maxResults(1)
                .optional()
                .orElse(null);
        Counterparty entity = existing != null ? existing : dataManager.create(Counterparty.class);
        entity.setInn(inn);
        entity.setName(trimTo(requisites.name(), 100));
        entity.setFullName(trimTo(requisites.fullName(), 1000));
        entity.setKpp(trimTo(requisites.kpp(), 9));
        if (existing == null) {
            entity.setCode("000000000");
            entity.setFolder(false);
            entity.setDeletionMark(false);
        }
        return dataManager.save(entity);
    }

    public record InnResolveResult(Counterparty counterparty, boolean foundInOneC) {
    }

    public record CounterpartyLinks(Counterparty counterparty,
                                    CounterpartyBankAccount bankAccount,
                                    CounterpartyContract contract) {
    }

    private Counterparty upsertCounterparty(OneCCatalogItem item) {
        Counterparty entity = loadByOneCRef(Counterparty.class, "vshd1_Counterparty", item.oneCRef());
        if (entity == null) {
            entity = dataManager.create(Counterparty.class);
            entity.setOneCRef(item.oneCRef());
        }
        entity.setCode(trimTo(item.code(), 9));
        entity.setName(item.name());
        entity.setFullName(item.fullName());
        entity.setInn(trimTo(item.inn(), 50));
        entity.setKpp(trimTo(item.kpp(), 9));
        entity.setFolder(false);
        entity.setDeletionMark(false);
        return dataManager.save(entity);
    }

    private Nomenclature upsertNomenclature(OneCCatalogItem item) {
        Nomenclature entity = loadByOneCRef(Nomenclature.class, "vshd1_Nomenclature", item.oneCRef());
        if (entity == null) {
            entity = dataManager.create(Nomenclature.class);
            entity.setOneCRef(item.oneCRef());
        }
        entity.setCode(trimTo(item.code(), 11));
        entity.setName(item.name());
        entity.setArticle(trimTo(item.article(), 50));
        entity.setService(item.service());
        entity.setFolder(false);
        entity.setDeletionMark(false);
        return dataManager.save(entity);
    }

    private CounterpartyBankAccount upsertBankAccount(Counterparty counterparty, OneCBankAccountItem item) {
        CounterpartyBankAccount entity = loadByOneCRef(
                CounterpartyBankAccount.class, "vshd1_CounterpartyBankAccount", item.oneCRef());
        if (entity == null) {
            entity = dataManager.create(CounterpartyBankAccount.class);
            entity.setOneCRef(item.oneCRef());
        }
        entity.setCounterparty(counterparty);
        entity.setCode(trimTo(item.code(), 9));
        entity.setName(item.name());
        entity.setAccountNumber(trimTo(item.accountNumber(), 70));
        return dataManager.save(entity);
    }

    private CounterpartyContract upsertContract(Counterparty counterparty, OneCContractItem item) {
        CounterpartyContract entity = loadByOneCRef(
                CounterpartyContract.class, "vshd1_CounterpartyContract", item.oneCRef());
        if (entity == null) {
            entity = dataManager.create(CounterpartyContract.class);
            entity.setOneCRef(item.oneCRef());
        }
        entity.setCounterparty(counterparty);
        entity.setCode(trimTo(item.code(), 9));
        entity.setName(item.name());
        entity.setContractKind(trimTo(item.contractKind(), 50));
        entity.setVatRate(trimTo(item.vatRate(), 50));
        entity.setAmountIncludesVat(item.amountIncludesVat());
        entity.setCurrencyKey(item.currencyKey());
        return dataManager.save(entity);
    }

    private <T> T loadByOneCRef(Class<T> entityClass, String entityName, UUID oneCRef) {
        return dataManager.load(entityClass)
                .query("select e from " + entityName + " e where e.oneCRef = :oneCRef")
                .parameter("oneCRef", oneCRef)
                .optional()
                .orElse(null);
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

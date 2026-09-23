package com.company.money.service;

import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.PaymentRequestNotice;
import com.company.money.entity.PaymentRequestStatus;
import com.company.money.entity.User;
import com.company.money.onec.OneCCatalogSearchService;
import com.company.money.onec.OneCOdataParser;
import com.company.money.security.PaymentRequestAccess;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentRequestService {

    static final String FD_DELETE_COMMENT = "Удален ФД";

    private final DataManager dataManager;
    private final OneCPaymentExportService oneCPaymentExportService;
    private final OneCCatalogSearchService oneCCatalogSearchService;
    private final PaymentRequestNoticeService noticeService;
    private final PaymentRequestAccess paymentRequestAccess;
    private final Messages messages;

    public PaymentRequestService(DataManager dataManager,
                                 OneCPaymentExportService oneCPaymentExportService,
                                 OneCCatalogSearchService oneCCatalogSearchService,
                                 PaymentRequestNoticeService noticeService,
                                 PaymentRequestAccess paymentRequestAccess,
                                 Messages messages) {
        this.dataManager = dataManager;
        this.oneCPaymentExportService = oneCPaymentExportService;
        this.oneCCatalogSearchService = oneCCatalogSearchService;
        this.noticeService = noticeService;
        this.paymentRequestAccess = paymentRequestAccess;
        this.messages = messages;
    }

    @Transactional
    public PaymentRequest submit(UUID requestId) {
        PaymentRequest request = loadForWorkflow(requestId);
        requireStatus(request, PaymentRequestStatus.DRAFT);
        if (request.getCounterparty() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needCounterparty"));
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needAmount"));
        }
        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needPurpose"));
        }
        if (request.isMissingInOneC()) {
            String inn = OneCCatalogSearchService.normalizeInn(request.getCounterparty().getInn());
            if (!OneCCatalogSearchService.isValidInn(inn)) {
                throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needInn"));
            }
            if (request.getRequestedPaymentDate() == null) {
                throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needRequestedDate"));
            }
        } else if (request.getCounterpartyBankAccount() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needBankAccount"));
        }
        List<PaymentRequestFile> attachments = request.getAttachments();
        if (attachments == null || attachments.isEmpty()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needAttachment"));
        }
        request.setStatus(PaymentRequestStatus.SUBMITTED);
        PaymentRequest saved = dataManager.save(request);
        if (saved.isMissingInOneC()) {
            noticeService.notifyFinancialDirectors(saved);
        }
        return saved;
    }

    @Transactional
    public PaymentRequest cancel(UUID requestId) {
        PaymentRequest request = loadForWorkflow(requestId);
        requireStatus(request,
                PaymentRequestStatus.DRAFT,
                PaymentRequestStatus.SUBMITTED,
                PaymentRequestStatus.DEFERRED);
        request.setStatus(PaymentRequestStatus.CANCELLED);
        return dataManager.save(request);
    }

    @Transactional
    public PaymentRequest approve(UUID requestId, LocalDate paymentDate, String comment) {
        PaymentRequest request = loadForWorkflow(requestId);
        requireStatus(request, PaymentRequestStatus.SUBMITTED, PaymentRequestStatus.DEFERRED);
        requireCounterpartyReadyForOneC(request);
        if (paymentDate == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needPaymentDate"));
        }
        request.setApprovedPaymentDate(paymentDate);
        request.setCfoComment(comment);
        request.setStatus(PaymentRequestStatus.APPROVED);
        PaymentRequest saved = dataManager.save(request);
        noticeService.notifyApplicant(saved);
        return saved;
    }

    @Transactional
    public PaymentRequest reject(UUID requestId, String comment) {
        PaymentRequest request = loadForWorkflow(requestId);
        requireStatus(request, PaymentRequestStatus.SUBMITTED, PaymentRequestStatus.DEFERRED);
        request.setCfoComment(comment);
        request.setStatus(PaymentRequestStatus.REJECTED);
        PaymentRequest saved = dataManager.save(request);
        noticeService.notifyApplicant(saved);
        return saved;
    }

    @Transactional
    public PaymentRequest defer(UUID requestId, LocalDate until) {
        PaymentRequest request = loadForWorkflow(requestId);
        requireStatus(request, PaymentRequestStatus.SUBMITTED, PaymentRequestStatus.DEFERRED);
        if (until == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needDeferDate"));
        }
        request.setDeferredUntil(until);
        request.setStatus(PaymentRequestStatus.DEFERRED);
        PaymentRequest saved = dataManager.save(request);
        noticeService.notifyApplicant(saved);
        return saved;
    }

    public PaymentRequest exportToOneC(UUID requestId) {
        return exportToOneC(requestId, OneCExportMode.APPROVE);
    }

    public PaymentRequest exportToOneC(UUID requestId, OneCExportMode mode) {
        if (mode == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needExportMode"));
        }
        PaymentRequest request = loadForWorkflow(requestId);
        requireExportable(request);
        requireCounterpartyReadyForOneC(request);
        request.setApprovedPaymentDate(resolveExportPaymentDate(request));
        User current = paymentRequestAccess.currentUser();
        if (current != null) {
            request.setLastChangedBy(current);
        }
        boolean restore = request.getOneCDocumentRef() != null && !request.getOneCDocumentRef().isBlank();
        OneCPaymentExportService.CreatedPaymentOrder created = restore
                ? oneCPaymentExportService.restorePaymentOrder(request)
                : oneCPaymentExportService.createPaymentOrder(request);
        request.setOneCExportPayload(created.payload());
        request.setOneCDocumentRef(created.refKey());
        request.setOneCDocumentNumber(created.number());
        request.setOneCDocumentDate(created.date());
        request.setStatus(PaymentRequestStatus.SENT_TO_1C);
        PaymentRequest saved = dataManager.save(request);
        oneCPaymentExportService.attachInvoiceFiles(created.refKey(), saved);
        oneCPaymentExportService.finishExport(saved, created.refKey(), mode);
        noticeService.notifyApplicant(saved);
        return saved;
    }

    @Transactional
    public PaymentRequest updateApprovedPaymentDate(UUID requestId, LocalDate paymentDate) {
        if (!paymentRequestAccess.isFinancialDirector()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotEditPaymentDate"));
        }
        PaymentRequest request = loadForWorkflow(requestId);
        PaymentRequestStatus status = request.getStatus();
        if (status == PaymentRequestStatus.SENT_TO_1C || status == PaymentRequestStatus.PAID) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotEditPaymentDate"));
        }
        request.setApprovedPaymentDate(paymentDate);
        User current = paymentRequestAccess.currentUser();
        if (current != null) {
            request.setLastChangedBy(current);
        }
        return dataManager.save(request);
    }

    @Transactional
    public PaymentRequest returnFromOneC(UUID requestId, OneCReturnAction action, LocalDate until, String comment) {
        PaymentRequest request = loadForWorkflow(requestId);
        if (action == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needReturnAction"));
        }
        if (action == OneCReturnAction.DEFER && until == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needDeferDate"));
        }
        String ref = request.getOneCDocumentRef();
        if (ref == null || ref.isBlank()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotReturnFromOneC"));
        }
        requireReturnablePaymentOrder(ref);
        oneCPaymentExportService.markPaymentOrderDeleted(ref);
        if (comment != null && !comment.isBlank()) {
            request.setCfoComment(comment);
        }
        switch (action) {
            case REVISION -> request.setStatus(PaymentRequestStatus.DRAFT);
            case DEFER -> {
                request.setDeferredUntil(until);
                request.setStatus(PaymentRequestStatus.DEFERRED);
            }
            case REJECT -> request.setStatus(PaymentRequestStatus.REJECTED);
        }
        PaymentRequest saved = dataManager.save(request);
        noticeService.notifyApplicant(saved);
        return saved;
    }

    @Transactional
    public void deleteRequest(UUID requestId) {
        PaymentRequest request = loadForWorkflow(requestId);
        if (paymentRequestAccess.isFinancialDirector()) {
            deleteByFinancialDirector(request);
            return;
        }
        if (!isDeletable(request)) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotDelete"));
        }
        deleteLinkedPaymentOrder(request, null);
        removeRequest(request);
    }

    public int returnMany(Collection<UUID> ids, OneCReturnAction action, LocalDate until, String comment) {
        return applyMany(ids, id -> returnFromOneC(id, action, until, comment));
    }

    public int syncExportedFromOneC() {
        if (!oneCPaymentExportService.isConfigured()) {
            return 0;
        }
        List<PaymentRequest> exported = dataManager.load(PaymentRequest.class)
                .query("""
                        select e from vshd1_PaymentRequest e
                        where e.status in :statuses and e.oneCDocumentRef is not null
                        """)
                .parameter("statuses", List.of(
                        PaymentRequestStatus.SENT_TO_1C,
                        PaymentRequestStatus.PAID))
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("applicant", FetchPlan.BASE))
                .list();
        int updated = 0;
        for (PaymentRequest request : exported) {
            if (applyOneCStatus(request)) {
                updated++;
            }
        }
        return updated;
    }

    public int approveMany(Collection<UUID> ids, LocalDate paymentDate, String comment) {
        return applyMany(ids, id -> approve(id, paymentDate, comment));
    }

    public int rejectMany(Collection<UUID> ids, String comment) {
        return applyMany(ids, id -> reject(id, comment));
    }

    public int deferMany(Collection<UUID> ids, LocalDate until) {
        return applyMany(ids, id -> defer(id, until));
    }

    public int exportMany(Collection<UUID> ids, OneCExportMode mode) {
        return applyMany(ids, id -> exportToOneC(id, mode));
    }

    @Transactional
    public PaymentRequest refreshCounterpartyFromOneC(UUID requestId) {
        PaymentRequest request = loadForWorkflow(requestId);
        if (request.getCounterparty() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needCounterparty"));
        }
        OneCCatalogSearchService.CounterpartyLinks links =
                oneCCatalogSearchService.loadLinksFromOneC(request.getCounterparty().getInn());
        request.setCounterparty(links.counterparty());
        request.setCounterpartyBankAccount(links.bankAccount());
        request.setContract(links.contract());
        request.setMissingInOneC(false);
        return dataManager.save(request);
    }

    public PaymentRequest loadForWorkflow(UUID requestId) {
        return dataManager.load(PaymentRequest.class)
                .id(requestId)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("applicant", FetchPlan.BASE)
                        .add("lastChangedBy", FetchPlan.BASE)
                        .add("counterparty", FetchPlan.BASE)
                        .add("nomenclature", FetchPlan.BASE)
                        .add("counterpartyBankAccount", FetchPlan.BASE)
                        .add("contract", FetchPlan.BASE)
                        .add("attachments", FetchPlan.BASE))
                .one();
    }

    public static boolean isDeletable(PaymentRequest request) {
        if (request == null) {
            return false;
        }
        PaymentRequestStatus status = request.getStatus();
        return status != PaymentRequestStatus.APPROVED
                && status != PaymentRequestStatus.SENT_TO_1C
                && status != PaymentRequestStatus.PAID;
    }

    public static boolean canReturn(PaymentRequest request) {
        if (request == null || request.getStatus() == PaymentRequestStatus.PAID) {
            return false;
        }
        String ref = request.getOneCDocumentRef();
        return ref != null && !ref.isBlank();
    }

    public boolean canDelete(PaymentRequest request) {
        if (request == null) {
            return false;
        }
        if (paymentRequestAccess.isFinancialDirector()) {
            return request.getStatus() != PaymentRequestStatus.PAID;
        }
        return isDeletable(request);
    }

    private boolean applyOneCStatus(PaymentRequest request) {
        try {
            OneCOdataParser.PaymentOrderState state = oneCPaymentExportService.getPaymentOrder(
                    request.getOneCDocumentRef());
            if (state == null) {
                return false;
            }
            PaymentRequestStatus next = state.paid()
                    ? PaymentRequestStatus.PAID
                    : PaymentRequestStatus.SENT_TO_1C;
            boolean changed = request.getStatus() != next;
            if (state.number() != null && !state.number().isBlank()) {
                changed = changed || !state.number().equals(request.getOneCDocumentNumber());
                request.setOneCDocumentNumber(state.number());
            }
            if (state.date() != null && !state.date().equals(request.getOneCDocumentDate())) {
                request.setOneCDocumentDate(state.date());
                changed = true;
            }
            if (!changed) {
                return false;
            }
            request.setStatus(next);
            PaymentRequest saved = dataManager.save(request);
            if (next == PaymentRequestStatus.PAID) {
                noticeService.notifyApplicant(saved);
            }
            return true;
        } catch (PaymentRequestException ex) {
            return false;
        }
    }

    private void deleteByFinancialDirector(PaymentRequest request) {
        if (request.getStatus() == PaymentRequestStatus.PAID) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotDeletePaid"));
        }
        deleteLinkedPaymentOrder(request, FD_DELETE_COMMENT);
        removeRequest(request);
    }

    private void deleteLinkedPaymentOrder(PaymentRequest request, String comment) {
        String ref = request.getOneCDocumentRef();
        if (ref == null || ref.isBlank()) {
            return;
        }
        oneCPaymentExportService.deletePaymentOrderWithAttachments(ref, comment);
    }

    private void removeRequest(PaymentRequest request) {
        dataManager.load(PaymentRequestFile.class)
                .query("select e from vshd1_PaymentRequestFile e where e.paymentRequest.id = :id")
                .parameter("id", request.getId())
                .list()
                .forEach(dataManager::remove);
        dataManager.load(PaymentRequestNotice.class)
                .query("select e from vshd1_PaymentRequestNotice e where e.paymentRequest.id = :id")
                .parameter("id", request.getId())
                .list()
                .forEach(dataManager::remove);
        PaymentRequest fresh = dataManager.load(PaymentRequest.class)
                .id(request.getId())
                .one();
        dataManager.remove(fresh);
    }

    private void requireReturnablePaymentOrder(String refKey) {
        OneCOdataParser.PaymentOrderState state = oneCPaymentExportService.getPaymentOrder(refKey);
        if (state == null || state.blocksReturn()) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.cannotReturnFromOneC"));
        }
    }

    private int applyMany(Collection<UUID> ids, WorkflowAction action) {
        int processed = 0;
        PaymentRequestException lastError = null;
        for (UUID id : ids) {
            try {
                action.run(id);
                processed++;
            } catch (PaymentRequestException ex) {
                lastError = ex;
            }
        }
        if (processed == 0 && lastError != null) {
            throw lastError;
        }
        return processed;
    }

    private LocalDate resolveExportPaymentDate(PaymentRequest request) {
        if (request.getApprovedPaymentDate() != null) {
            return request.getApprovedPaymentDate();
        }
        if (request.getDeferredUntil() != null) {
            return request.getDeferredUntil();
        }
        if (request.getRequestedPaymentDate() != null) {
            return request.getRequestedPaymentDate();
        }
        throw new PaymentRequestException(
                messages.getMessage("com.company.money/paymentRequest.error.needPaymentDateBeforeExport"));
    }

    private void requireExportable(PaymentRequest request) {
        requireStatus(request, PaymentRequestStatus.APPROVED);
    }

    private void requireCounterpartyReadyForOneC(PaymentRequest request) {
        if (request.isMissingInOneC()) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/paymentRequest.error.needCounterpartyInOneC"));
        }
        if (request.getCounterpartyBankAccount() == null) {
            throw new PaymentRequestException(messages.getMessage("com.company.money/paymentRequest.error.needBankAccount"));
        }
    }

    private void requireStatus(PaymentRequest request, PaymentRequestStatus... expected) {
        PaymentRequestStatus status = request.getStatus();
        for (PaymentRequestStatus candidate : expected) {
            if (status == candidate) {
                return;
            }
        }
        throw new PaymentRequestException(
                messages.formatMessage("com.company.money", "paymentRequest.error.wrongStatus", status));
    }

    @FunctionalInterface
    private interface WorkflowAction {
        void run(UUID id);
    }
}

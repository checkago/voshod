package com.company.money.service;

import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestNotice;
import com.company.money.entity.User;
import com.company.money.security.FinancialDirectorRole;
import com.company.money.security.FullAccessRole;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.Messages;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class PaymentRequestNoticeService {

    private final UnconstrainedDataManager dataManager;
    private final Messages messages;

    public PaymentRequestNoticeService(DataManager dataManager, Messages messages) {
        this.dataManager = dataManager.unconstrained();
        this.messages = messages;
    }

    @Transactional
    public void notifyApplicant(PaymentRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        PaymentRequest loaded = dataManager.load(PaymentRequest.class)
                .id(request.getId())
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("applicant", FetchPlan.BASE))
                .optional()
                .orElse(null);
        if (loaded == null || loaded.getApplicant() == null) {
            return;
        }
        String statusCaption = messages.getMessage(loaded.getStatus());
        String comment = loaded.getCfoComment();
        String text;
        if (comment != null && !comment.isBlank()) {
            text = messages.formatMessage("com.company.money", "paymentRequest.notice.statusWithComment",
                    loaded.getRequestNumber(), statusCaption, comment);
        } else {
            text = messages.formatMessage("com.company.money", "paymentRequest.notice.status",
                    loaded.getRequestNumber(), statusCaption);
        }
        PaymentRequestNotice notice = dataManager.create(PaymentRequestNotice.class);
        notice.setPaymentRequest(loaded);
        notice.setRecipient(loaded.getApplicant());
        notice.setMessageText(text);
        notice.setReadFlag(false);
        dataManager.saveWithoutReload(notice);
    }

    @Transactional
    public void notifyFinancialDirectors(PaymentRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        PaymentRequest loaded = dataManager.load(PaymentRequest.class)
                .id(request.getId())
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("counterparty", FetchPlan.BASE)
                        .add("applicant", FetchPlan.BASE))
                .optional()
                .orElse(null);
        if (loaded == null || loaded.getCounterparty() == null) {
            return;
        }
        String name = loaded.getCounterparty().getName();
        String inn = loaded.getCounterparty().getInn() == null ? "" : loaded.getCounterparty().getInn();
        String text = messages.formatMessage("com.company.money", "paymentRequest.notice.createCounterparty",
                loaded.getRequestNumber(), name, inn);
        for (User recipient : financialDirectorRecipients()) {
            PaymentRequestNotice notice = dataManager.create(PaymentRequestNotice.class);
            notice.setPaymentRequest(loaded);
            notice.setRecipient(recipient);
            notice.setMessageText(text);
            notice.setReadFlag(false);
            dataManager.saveWithoutReload(notice);
        }
    }

    public List<PaymentRequestNotice> unreadFor(User recipient) {
        if (recipient == null) {
            return List.of();
        }
        return dataManager.load(PaymentRequestNotice.class)
                .query("""
                        select e from vshd1_PaymentRequestNotice e
                        where e.recipient = :recipient and e.readFlag = false
                        order by e.createdAt desc
                        """)
                .parameter("recipient", recipient)
                .fetchPlan(builder -> builder
                        .addFetchPlan(FetchPlan.BASE)
                        .add("paymentRequest", FetchPlan.INSTANCE_NAME)
                        .add("recipient", FetchPlan.INSTANCE_NAME))
                .list();
    }

    @Transactional
    public void markRead(List<PaymentRequestNotice> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        for (PaymentRequestNotice notice : notices) {
            notice.setReadFlag(true);
            dataManager.saveWithoutReload(notice);
        }
    }

    private List<User> financialDirectorRecipients() {
        List<RoleAssignmentEntity> assignments = dataManager.load(RoleAssignmentEntity.class)
                .query("""
                        select e from sec_RoleAssignmentEntity e
                        where e.roleCode in :codes and e.roleType = :roleType
                        """)
                .parameter("codes", List.of(FinancialDirectorRole.CODE, FullAccessRole.CODE))
                .parameter("roleType", "resource")
                .list();
        Set<String> usernames = new HashSet<>();
        for (RoleAssignmentEntity assignment : assignments) {
            if (assignment.getUsername() != null && !assignment.getUsername().isBlank()) {
                usernames.add(assignment.getUsername());
            }
        }
        if (usernames.isEmpty()) {
            return List.of();
        }
        return dataManager.load(User.class)
                .query("select e from vshd1_User e where e.username in :usernames and e.active = true")
                .parameter("usernames", usernames)
                .list();
    }
}

package com.company.money.listener;

import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestStatus;
import com.company.money.entity.User;
import com.company.money.service.PaymentRequestException;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.Messages;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
public class PaymentRequestSaveListener {

    private final DataManager dataManager;
    private final CurrentAuthentication currentAuthentication;
    private final Messages messages;
    private final EntityStates entityStates;

    public PaymentRequestSaveListener(DataManager dataManager,
                                      CurrentAuthentication currentAuthentication,
                                      Messages messages,
                                      EntityStates entityStates) {
        this.dataManager = dataManager;
        this.currentAuthentication = currentAuthentication;
        this.messages = messages;
        this.entityStates = entityStates;
    }

    @EventListener
    public void onSaving(EntitySavingEvent<PaymentRequest> event) {
        PaymentRequest request = event.getEntity();
        User current = currentUser();
        if (entityStates.isLoaded(request, "applicant") && request.getApplicant() == null) {
            request.setApplicant(current);
        }
        if (entityStates.isNew(request)
                || (entityStates.isLoaded(request, "lastChangedBy") && request.getLastChangedBy() == null)) {
            request.setLastChangedBy(current);
        }
        if (request.getRequestNumber() == null
                || request.getRequestNumber().startsWith("TMP-")) {
            request.setRequestNumber(nextNumber());
        }
    }

    @EventListener
    public void onChanged(EntityChangedEvent<PaymentRequest> event) {
        if (event.getType() != EntityChangedEvent.Type.DELETED) {
            return;
        }
        Object oldStatus = event.getChanges().getOldValue("status");
        PaymentRequestStatus status = null;
        if (oldStatus instanceof PaymentRequestStatus parsed) {
            status = parsed;
        } else if (oldStatus instanceof String id) {
            status = PaymentRequestStatus.fromId(id);
        }
        if (status == PaymentRequestStatus.PAID) {
            throw new PaymentRequestException(
                    messages.getMessage("com.company.money/paymentRequest.error.cannotDeletePaid"));
        }
    }

    private User currentUser() {
        UserDetails userDetails = currentAuthentication.getUser();
        if (userDetails instanceof User user) {
            return dataManager.load(User.class)
                    .id(user.getId())
                    .one();
        }
        return dataManager.load(User.class)
                .query("select e from vshd1_User e where e.username = :username")
                .parameter("username", userDetails.getUsername())
                .one();
    }

    private String nextNumber() {
        int year = Year.now().getValue();
        String prefix = "ЗОП-" + year + "-";
        String last = dataManager.loadValue(
                        "select max(e.requestNumber) from vshd1_PaymentRequest e where e.requestNumber like :prefix",
                        String.class)
                .parameter("prefix", prefix + "%")
                .optional()
                .orElse(null);
        int next = 1;
        if (last != null && last.length() > prefix.length()) {
            try {
                next = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        return prefix + String.format("%06d", next);
    }
}

package com.company.money.security;

import com.company.money.entity.PaymentRequest;
import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;

@RowLevelRole(name = "Own payment requests", code = OwnPaymentRequestsRole.CODE)
public interface OwnPaymentRequestsRole {

    String CODE = "own-payment-requests";

    @JpqlRowLevelPolicy(entityClass = PaymentRequest.class,
            where = "{E}.applicant.username = :current_user_username")
    void paymentRequests();
}

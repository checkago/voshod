package com.company.money.security;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.Nomenclature;
import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.PaymentRequestNotice;
import com.company.money.entity.User;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(name = "Financial director", code = FinancialDirectorRole.CODE, scope = SecurityScope.UI)
public interface FinancialDirectorRole extends UiMinimalRole {

    String CODE = "financial-director";

    @EntityAttributePolicy(entityClass = PaymentRequest.class, attributes = {
            "approvedPaymentDate", "cfoComment", "status", "oneCDocumentRef",
            "oneCDocumentNumber", "oneCDocumentDate", "oneCExportPayload", "deferredUntil",
            "lastChangedBy", "counterparty", "counterpartyBankAccount", "contract", "missingInOneC"
    }, action = EntityAttributePolicyAction.MODIFY)
    @EntityAttributePolicy(entityClass = PaymentRequest.class, attributes = {
            "requestNumber", "requestDate", "applicant", "nomenclature",
            "amount", "purpose", "requestedPaymentDate",
            "attachments", "oneCDocumentTitle"
    }, action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = PaymentRequest.class, actions = {
            EntityPolicyAction.READ, EntityPolicyAction.UPDATE, EntityPolicyAction.DELETE
    })
    void paymentRequest();

    @EntityAttributePolicy(entityClass = PaymentRequestFile.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = PaymentRequestFile.class, actions = {
            EntityPolicyAction.READ, EntityPolicyAction.DELETE
    })
    void paymentRequestFile();

    @EntityAttributePolicy(entityClass = PaymentRequestNotice.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = PaymentRequestNotice.class, actions = {
            EntityPolicyAction.READ, EntityPolicyAction.DELETE
    })
    void paymentRequestNotice();

    @EntityAttributePolicy(entityClass = Counterparty.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Counterparty.class, actions = {EntityPolicyAction.READ})
    void counterparty();

    @EntityAttributePolicy(entityClass = CounterpartyBankAccount.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = CounterpartyBankAccount.class, actions = {EntityPolicyAction.READ})
    void counterpartyBankAccount();

    @EntityAttributePolicy(entityClass = CounterpartyContract.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = CounterpartyContract.class, actions = {EntityPolicyAction.READ})
    void counterpartyContract();

    @EntityAttributePolicy(entityClass = Nomenclature.class, attributes = "*",
            action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = Nomenclature.class, actions = {EntityPolicyAction.READ})
    void nomenclature();

    @EntityAttributePolicy(entityClass = User.class, attributes = {
            "username", "firstName", "lastName", "employeeFullName"
    }, action = EntityAttributePolicyAction.VIEW)
    @EntityPolicy(entityClass = User.class, actions = {EntityPolicyAction.READ})
    void user();

    @ViewPolicy(viewIds = {
            "vshd1_PaymentRequest.list",
            "vshd1_PaymentRequest.detail",
            "vshd1_PaymentRequestFile.detail"
    })
    @MenuPolicy(menuIds = "vshd1_PaymentRequest.list")
    void screens();
}

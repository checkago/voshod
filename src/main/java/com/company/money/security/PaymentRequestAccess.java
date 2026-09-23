package com.company.money.security;

import com.company.money.entity.User;
import io.jmix.core.security.CurrentAuthentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestAccess {

    private final CurrentAuthentication currentAuthentication;

    public PaymentRequestAccess(CurrentAuthentication currentAuthentication) {
        this.currentAuthentication = currentAuthentication;
    }

    public boolean isFinancialDirector() {
        return hasRole(FinancialDirectorRole.CODE) || hasRole(FullAccessRole.CODE);
    }

    public boolean isEmployeeActor() {
        return hasRole(EmployeeRole.CODE) || hasRole(FullAccessRole.CODE);
    }

    public User currentUser() {
        if (currentAuthentication.getUser() instanceof User user) {
            return user;
        }
        return null;
    }

    private boolean hasRole(String roleCode) {
        return currentAuthentication.getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(roleCode) || authority.endsWith(roleCode));
    }
}

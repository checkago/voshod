package com.company.money.security;

import io.jmix.security.model.SecurityScope;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.UiMinimalPolicies;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

@ResourceRole(name = "UI: minimal access", code = UiMinimalRole.CODE, scope = SecurityScope.UI)
public interface UiMinimalRole extends UiMinimalPolicies {

    String CODE = "ui-minimal";

    @ViewPolicy(viewIds = "vshd1_MainView")
    void main();

    @ViewPolicy(viewIds = "vshd1_LoginView")
    @SpecificPolicy(resources = "ui.loginToUi")
    void login();
}

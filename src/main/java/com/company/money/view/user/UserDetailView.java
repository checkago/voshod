package com.company.money.view.user;

import com.company.money.entity.User;
import com.company.money.onec.OneCCatalogItem;
import com.company.money.onec.OneCCatalogSearchService;
import com.company.money.service.PaymentRequestException;
import com.company.money.view.main.MainView;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Stream;

@Route(value = "users/:id", layout = MainView.class)
@ViewController(id = "vshd1_User.detail")
@ViewDescriptor(path = "user-detail-view.xml")
@EditedEntityContainer("userDc")
public class UserDetailView extends StandardDetailView<User> {

    @ViewComponent
    private TypedTextField<String> usernameField;
    @ViewComponent
    private PasswordField passwordField;
    @ViewComponent
    private PasswordField confirmPasswordField;
    @ViewComponent
    private ComboBox<String> timeZoneField;
    @ViewComponent
    private JmixComboBox<OneCCatalogItem> employeeField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private Notifications notifications;

    @Autowired
    private EntityStates entityStates;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OneCCatalogSearchService oneCCatalogSearchService;

    private boolean newEntity;

    @Subscribe
    public void onInit(final InitEvent event) {
        timeZoneField.setItems(List.of(TimeZone.getAvailableIDs()));
        employeeField.setPageSize(20);
        employeeField.setItemLabelGenerator(item -> item == null ? "" : item.name());
        employeeField.setItemsFetchCallback(this::fetchEmployees);
        employeeField.addValueChangeListener(change -> {
            if (change.isFromClient() || change.getValue() != null) {
                applyEmployee(change.getValue());
            }
        });
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<User> event) {
        usernameField.setReadOnly(false);
        passwordField.setVisible(true);
        confirmPasswordField.setVisible(true);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        User user = getEditedEntity();
        if (user.getOneCEmployeeRef() != null && user.getEmployeeFullName() != null) {
            employeeField.setValue(new OneCCatalogItem(
                    user.getOneCEmployeeRef(),
                    user.getEmployeeCode(),
                    user.getEmployeeFullName(),
                    null, null, null, null, false));
        }
        if (entityStates.isNew(user)) {
            usernameField.focus();
        }
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (entityStates.isNew(getEditedEntity())
                && !Objects.equals(passwordField.getValue(), confirmPasswordField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("passwordsDoNotMatch"));
        }
    }

    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            getEditedEntity().setPassword(passwordEncoder.encode(passwordField.getValue()));

            newEntity = true;
        }
    }

    @Subscribe
    public void onAfterSave(final AfterSaveEvent event) {
        if (newEntity) {
            notifications.create(messageBundle.getMessage("noAssignedRolesNotification"))
                    .withThemeVariant(NotificationVariant.WARNING)
                    .withPosition(Notification.Position.TOP_END)
                    .show();

            newEntity = false;
        }
    }

    private Stream<OneCCatalogItem> fetchEmployees(Query<OneCCatalogItem, String> query) {
        try {
            return oneCCatalogSearchService
                    .searchEmployees(query.getFilter().orElse(""), query.getOffset(), query.getLimit())
                    .stream();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            return Stream.empty();
        }
    }

    private void applyEmployee(OneCCatalogItem item) {
        User user = getEditedEntity();
        if (item == null) {
            user.setOneCEmployeeRef(null);
            user.setEmployeeFullName(null);
            user.setEmployeeCode(null);
            return;
        }
        user.setOneCEmployeeRef(item.oneCRef());
        user.setEmployeeFullName(item.name());
        user.setEmployeeCode(item.code());
        String[] parts = item.name().trim().split("\\s+", 2);
        user.setLastName(parts[0]);
        user.setFirstName(parts.length > 1 ? parts[1] : "");
    }
}

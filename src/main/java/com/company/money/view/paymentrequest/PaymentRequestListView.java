package com.company.money.view.paymentrequest;

import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestNotice;
import com.company.money.entity.PaymentRequestStatus;
import com.company.money.entity.User;
import com.company.money.security.PaymentRequestAccess;
import com.company.money.service.OneCExportMode;
import com.company.money.service.OneCReturnAction;
import com.company.money.service.PaymentRequestException;
import com.company.money.service.PaymentRequestNoticeService;
import com.company.money.service.PaymentRequestService;
import com.company.money.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.action.list.RemoveAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Route(value = "payment-requests", layout = MainView.class)
@ViewController(id = "vshd1_PaymentRequest.list")
@ViewDescriptor(path = "payment-request-list-view.xml")
@LookupComponent("paymentRequestsDataGrid")
@DialogMode(width = "80em")
public class PaymentRequestListView extends StandardListView<PaymentRequest> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private CollectionLoader<PaymentRequest> paymentRequestsDl;
    @ViewComponent
    private DataGrid<PaymentRequest> paymentRequestsDataGrid;
    @ViewComponent
    private Component fdFilters;
    @ViewComponent
    private JmixComboBox<User> applicantFilterField;
    @ViewComponent
    private TypedDatePicker<LocalDate> periodFromField;
    @ViewComponent
    private TypedDatePicker<LocalDate> periodToField;
    @ViewComponent
    private JmixButton createButton;
    @ViewComponent
    private JmixButton removeButton;
    @ViewComponent
    private JmixButton approveButton;
    @ViewComponent
    private JmixButton rejectButton;
    @ViewComponent
    private JmixButton deferButton;
    @ViewComponent
    private JmixButton exportButton;
    @ViewComponent
    private JmixButton returnButton;

    @Autowired
    private PaymentRequestAccess paymentRequestAccess;
    @Autowired
    private PaymentRequestService paymentRequestService;
    @Autowired
    private PaymentRequestNoticeService noticeService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private DataManager dataManager;

    @Subscribe
    public void onInit(final InitEvent event) {
        boolean financialDirector = paymentRequestAccess.isFinancialDirector();
        boolean employee = paymentRequestAccess.isEmployeeActor();
        fdFilters.setVisible(financialDirector);
        approveButton.setVisible(financialDirector);
        rejectButton.setVisible(financialDirector);
        deferButton.setVisible(financialDirector);
        exportButton.setVisible(financialDirector);
        returnButton.setVisible(financialDirector);
        createButton.setVisible(employee);
        removeButton.setVisible(employee || financialDirector);
        if (financialDirector) {
            paymentRequestsDataGrid.setSelectionMode(Grid.SelectionMode.MULTI);
            var editorActions = paymentRequestsDataGrid.getColumnByKey("editorActions");
            if (editorActions != null) {
                editorActions.setVisible(true);
            }
            paymentRequestsDataGrid.getEditor().addSaveListener(saveEvent -> saveApprovedPaymentDate(saveEvent.getItem()));
        }
        applyRowColorParts();
        configureRemoveAction(financialDirector);
        applicantFilterField.setItemLabelGenerator(user -> user == null ? "" : user.getDisplayName());
        applicantFilterField.setPageSize(20);
        applicantFilterField.setItemsFetchCallback(this::fetchApplicants);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        applyFilters();
        super.beforeEnter(event);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        applicantFilterField.addValueChangeListener(change -> reload());
        periodFromField.addValueChangeListener(change -> reload());
        periodToField.addValueChangeListener(change -> reload());
        showUnreadNotices();
        if (paymentRequestService.syncExportedFromOneC() > 0) {
            reload();
        }
    }

    @Subscribe("approveButton")
    public void onApproveClick(final ClickEvent<JmixButton> event) {
        Set<UUID> ids = selectedIds();
        if (ids.isEmpty()) {
            notifications.create(messageBundle.getMessage("needSelection")).show();
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("approveDialog.header"))
                .withParameters(InputParameter.localDateParameter("date")
                        .withLabel(messageBundle.getMessage("approveDialog.date"))
                        .withRequired(true)
                        .withDefaultValue(LocalDate.now()))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        runBulk(() -> paymentRequestService.approveMany(ids, closeEvent.getValue("date"), null));
                    }
                })
                .open();
    }

    @Subscribe("rejectButton")
    public void onRejectClick(final ClickEvent<JmixButton> event) {
        Set<UUID> ids = selectedIds();
        if (ids.isEmpty()) {
            notifications.create(messageBundle.getMessage("needSelection")).show();
            return;
        }
        runBulk(() -> paymentRequestService.rejectMany(ids, null));
    }

    @Subscribe("deferButton")
    public void onDeferClick(final ClickEvent<JmixButton> event) {
        Set<UUID> ids = selectedIds();
        if (ids.isEmpty()) {
            notifications.create(messageBundle.getMessage("needSelection")).show();
            return;
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("deferDialog.header"))
                .withParameters(InputParameter.localDateParameter("date")
                        .withLabel(messageBundle.getMessage("deferDialog.date"))
                        .withRequired(true)
                        .withDefaultValue(LocalDate.now()))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        runBulk(() -> paymentRequestService.deferMany(ids, closeEvent.getValue("date")));
                    }
                })
                .open();
    }

    @Subscribe("exportButton")
    public void onExportClick(final ClickEvent<JmixButton> event) {
        Set<UUID> ids = selectedIds();
        if (ids.isEmpty()) {
            notifications.create(messageBundle.getMessage("needSelection")).show();
            return;
        }
        openExportDialog(ids);
    }

    @Subscribe("returnButton")
    public void onReturnClick(final ClickEvent<JmixButton> event) {
        Set<UUID> ids = selectedIds();
        if (ids.isEmpty()) {
            notifications.create(messageBundle.getMessage("needSelection")).show();
            return;
        }
        openReturnDialog(ids);
    }

    @Install(to = "paymentRequestsDataGrid.removeAction", subject = "enabledRule")
    private boolean removeEnabledRule() {
        var selected = paymentRequestsDataGrid.getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            return false;
        }
        return selected.stream().allMatch(paymentRequestService::canDelete);
    }

    @Install(to = "paymentRequestsDataGrid.removeAction", subject = "delegate")
    private void removeDelegate(Collection<PaymentRequest> requests) {
        runBulk(() -> {
            int processed = 0;
            for (PaymentRequest request : requests) {
                paymentRequestService.deleteRequest(request.getId());
                processed++;
            }
            return processed;
        });
    }

    private void configureRemoveAction(boolean financialDirector) {
        if (!(paymentRequestsDataGrid.getAction("removeAction") instanceof RemoveAction<?> removeAction)) {
            return;
        }
        @SuppressWarnings("unchecked")
        RemoveAction<PaymentRequest> typed = (RemoveAction<PaymentRequest>) removeAction;
        if (financialDirector) {
            typed.setConfirmationHeader(messageBundle.getMessage("deleteFdConfirm.header"));
            typed.setConfirmationText(messageBundle.getMessage("deleteFdConfirm.text"));
        } else {
            typed.setConfirmationHeader(messageBundle.getMessage("deleteConfirm.header"));
            typed.setConfirmationText(messageBundle.getMessage("deleteConfirm.text"));
        }
    }

    private void applyFilters() {
        StringBuilder query = new StringBuilder(
                "select e from vshd1_PaymentRequest e where 1=1 ");
        if (!paymentRequestAccess.isFinancialDirector()) {
            User user = paymentRequestAccess.currentUser();
            if (user == null) {
                query.append("and 1=0 ");
            } else {
                query.append("and e.applicant.id = :applicantId ");
                paymentRequestsDl.setParameter("applicantId", user.getId());
            }
        } else {
            User applicant = applicantFilterField.getValue();
            if (applicant != null) {
                query.append("and e.applicant.id = :applicantId ");
                paymentRequestsDl.setParameter("applicantId", applicant.getId());
            } else {
                paymentRequestsDl.removeParameter("applicantId");
            }
            LocalDate from = periodFromField.getValue();
            if (from != null) {
                query.append("and e.requestDate >= :dateFrom ");
                paymentRequestsDl.setParameter("dateFrom", from);
            } else {
                paymentRequestsDl.removeParameter("dateFrom");
            }
            LocalDate to = periodToField.getValue();
            if (to != null) {
                query.append("and e.requestDate <= :dateTo ");
                paymentRequestsDl.setParameter("dateTo", to);
            } else {
                paymentRequestsDl.removeParameter("dateTo");
            }
        }
        query.append("order by e.requestDate desc, e.requestNumber desc");
        paymentRequestsDl.setQuery(query.toString());
    }

    private void showUnreadNotices() {
        User user = paymentRequestAccess.currentUser();
        List<PaymentRequestNotice> unread = noticeService.unreadFor(user);
        for (PaymentRequestNotice notice : unread) {
            notifications.create(notice.getMessageText()).show();
        }
        noticeService.markRead(unread);
    }

    private void openExportDialog(Set<UUID> ids) {
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("exportDialog.header"))
                .withText(messageBundle.getMessage("exportDialog.text"))
                .withActions(
                        new DialogAction(DialogAction.Type.OK)
                                .withText(messageBundle.getMessage("exportDialog.prepare"))
                                .withHandler(e -> runBulk(() -> paymentRequestService.exportMany(
                                        ids, OneCExportMode.PREPARE))),
                        new DialogAction(DialogAction.Type.YES)
                                .withText(messageBundle.getMessage("exportDialog.approve"))
                                .withHandler(e -> runBulk(() -> paymentRequestService.exportMany(
                                        ids, OneCExportMode.APPROVE))),
                        new DialogAction(DialogAction.Type.CANCEL))
                .open();
    }

    private void saveApprovedPaymentDate(PaymentRequest request) {
        if (request == null || request.getId() == null) {
            return;
        }
        try {
            paymentRequestService.updateApprovedPaymentDate(request.getId(), request.getApprovedPaymentDate());
            reload();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            reload();
        }
    }

    private void openReturnDialog(Set<UUID> ids) {
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("returnDialog.header"))
                .withText(messageBundle.getMessage("returnDialog.text"))
                .withActions(
                        new DialogAction(DialogAction.Type.OK)
                                .withText(messageBundle.getMessage("returnDialog.revision"))
                                .withHandler(e -> runBulk(() -> paymentRequestService.returnMany(
                                        ids, OneCReturnAction.REVISION, null, null))),
                        new DialogAction(DialogAction.Type.YES)
                                .withText(messageBundle.getMessage("returnDialog.defer"))
                                .withHandler(e -> openReturnDeferDialog(ids)),
                        new DialogAction(DialogAction.Type.NO)
                                .withText(messageBundle.getMessage("returnDialog.reject"))
                                .withHandler(e -> runBulk(() -> paymentRequestService.returnMany(
                                        ids, OneCReturnAction.REJECT, null, null))),
                        new DialogAction(DialogAction.Type.CANCEL))
                .open();
    }

    private void openReturnDeferDialog(Set<UUID> ids) {
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("deferDialog.header"))
                .withParameters(InputParameter.localDateParameter("date")
                        .withLabel(messageBundle.getMessage("deferDialog.date"))
                        .withRequired(true)
                        .withDefaultValue(LocalDate.now()))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        runBulk(() -> paymentRequestService.returnMany(
                                ids, OneCReturnAction.DEFER, closeEvent.getValue("date"), null));
                    }
                })
                .open();
    }

    private void reload() {
        applyFilters();
        paymentRequestsDl.load();
    }

    private Stream<User> fetchApplicants(Query<User, String> query) {
        String text = query.getFilter().orElse("").trim();
        var loader = dataManager.load(User.class);
        List<User> users;
        if (text.isEmpty()) {
            users = loader.query("select e from vshd1_User e where e.active = true order by e.lastName, e.username")
                    .firstResult(query.getOffset())
                    .maxResults(query.getLimit())
                    .list();
        } else {
            String pattern = "%" + text.toLowerCase() + "%";
            users = loader.query("""
                            select e from vshd1_User e
                            where e.active = true
                              and (lower(e.username) like :pattern
                                or lower(e.lastName) like :pattern
                                or lower(e.firstName) like :pattern
                                or lower(e.employeeFullName) like :pattern)
                            order by e.lastName, e.username
                            """)
                    .parameter("pattern", pattern)
                    .firstResult(query.getOffset())
                    .maxResults(query.getLimit())
                    .list();
        }
        return users.stream();
    }

    private Set<UUID> selectedIds() {
        return paymentRequestsDataGrid.getSelectedItems().stream()
                .map(PaymentRequest::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private void runBulk(BulkAction action) {
        try {
            int processed = action.run();
            reload();
            notifications.create(messageBundle.getMessage("bulkSuccess").formatted(processed)).show();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
        }
    }

    private void applyRowColorParts() {
        paymentRequestsDataGrid.setPartNameGenerator(this::rowPartName);
        paymentRequestsDataGrid.getColumns()
                .forEach(column -> column.setPartNameGenerator(this::rowPartName));
    }

    private String rowPartName(PaymentRequest request) {
        if (request == null) {
            return "pr-draft";
        }
        PaymentRequestStatus status = request.getStatus();
        if (status != PaymentRequestStatus.DRAFT && request.isMissingInOneC()) {
            return "pr-missing-1c";
        }
        if (status == PaymentRequestStatus.SUBMITTED) {
            return "pr-submitted";
        }
        if (status == PaymentRequestStatus.DEFERRED) {
            return "pr-deferred";
        }
        if (status == PaymentRequestStatus.APPROVED) {
            return "pr-approved";
        }
        if (status == PaymentRequestStatus.SENT_TO_1C) {
            return "pr-sent";
        }
        if (status == PaymentRequestStatus.PAID) {
            return "pr-paid";
        }
        if (status == PaymentRequestStatus.REJECTED || status == PaymentRequestStatus.CANCELLED) {
            return "pr-cancelled";
        }
        return "pr-draft";
    }

    @FunctionalInterface
    private interface BulkAction {
        int run();
    }
}

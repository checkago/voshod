package com.company.money.view.paymentrequest;

import com.company.money.entity.Counterparty;
import com.company.money.entity.CounterpartyBankAccount;
import com.company.money.entity.CounterpartyContract;
import com.company.money.entity.Nomenclature;
import com.company.money.entity.PaymentRequest;
import com.company.money.entity.PaymentRequestFile;
import com.company.money.entity.PaymentRequestStatus;
import com.company.money.entity.User;
import com.company.money.onec.OneCCatalogSearchService;
import com.company.money.security.PaymentRequestAccess;
import com.company.money.service.OneCExportMode;
import com.company.money.service.OneCReturnAction;
import com.company.money.service.PaymentRequestException;
import com.company.money.service.PaymentRequestService;
import com.company.money.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FileRef;
import io.jmix.core.Messages;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.app.inputdialog.DialogActions;
import io.jmix.flowui.app.inputdialog.DialogOutcome;
import io.jmix.flowui.app.inputdialog.InputParameter;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.download.Downloader;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.DataContext;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Route(value = "payment-requests/:id", layout = MainView.class)
@ViewController(id = "vshd1_PaymentRequest.detail")
@ViewDescriptor(path = "payment-request-detail-view.xml")
@EditedEntityContainer("paymentRequestDc")
public class PaymentRequestDetailView extends StandardDetailView<PaymentRequest> {

    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private JmixButton submitButton;
    @ViewComponent
    private JmixButton cancelButton;
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
    @ViewComponent
    private EntityComboBox<Counterparty> counterpartyField;
    @ViewComponent
    private EntityComboBox<Nomenclature> nomenclatureField;
    @ViewComponent
    private EntityComboBox<CounterpartyBankAccount> bankAccountField;
    @ViewComponent
    private EntityComboBox<CounterpartyContract> contractField;
    @ViewComponent
    private TypedTextField<String> innLookupField;
    @ViewComponent
    private JmixButton fillByInnButton;
    @ViewComponent
    private JmixButton checkCounterpartyButton;
    @ViewComponent
    private Span fdMissingNotice;
    @ViewComponent
    private TypedDatePicker<LocalDate> requestedPaymentDateField;
    @ViewComponent
    private FileStorageUploadField attachFileField;
    @ViewComponent
    private CollectionContainer<PaymentRequestFile> attachmentsDc;
    @ViewComponent
    private DataGrid<PaymentRequestFile> attachmentsDataGrid;
    @ViewComponent
    private DataContext dataContext;

    @Autowired
    private PaymentRequestService paymentRequestService;
    @Autowired
    private OneCCatalogSearchService oneCCatalogSearchService;
    @Autowired
    private Notifications notifications;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private PaymentRequestAccess paymentRequestAccess;
    @Autowired
    private Downloader downloader;
    @Autowired
    private Messages messages;

    @Subscribe
    public void onInit(final InitEvent event) {
        counterpartyField.setPageSize(20);
        counterpartyField.setItemsFetchCallback(this::fetchCounterparties);
        counterpartyField.addValueChangeListener(change -> {
            if (change.isFromClient()) {
                getEditedEntity().setMissingInOneC(false);
                getEditedEntity().setCounterpartyBankAccount(null);
                bankAccountField.setValue(null);
                if (change.getValue() != null && change.getValue().getInn() != null) {
                    innLookupField.setValue(change.getValue().getInn());
                }
                if (bankAccountField.getDataProvider() != null) {
                    bankAccountField.getDataProvider().refreshAll();
                }
                applyMainContract(change.getValue());
                refreshMissingCounterpartyUi();
            }
        });
        nomenclatureField.setPageSize(20);
        nomenclatureField.setItemsFetchCallback(this::fetchNomenclature);
        bankAccountField.setPageSize(20);
        bankAccountField.setItemsFetchCallback(this::fetchBankAccounts);
        contractField.setPageSize(20);
        contractField.setItemsFetchCallback(this::fetchContracts);
        attachFileField.addFileUploadSucceededListener(uploadEvent -> attachUploadedFile());
        innLookupField.setMaxLength(12);
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<PaymentRequest> event) {
        User current = currentPortalUser();
        if (current != null) {
            event.getEntity().setApplicant(current);
            event.getEntity().setLastChangedBy(current);
        }
    }

    @Subscribe
    public void onBeforeSave(final BeforeSaveEvent event) {
        User current = currentPortalUser();
        if (current != null) {
            getEditedEntity().setLastChangedBy(current);
        }
        if (getEditedEntity().getCounterparty() == null
                && innLookupField.getValue() != null
                && !innLookupField.getValue().isBlank()) {
            try {
                fillByInn(false);
            } catch (PaymentRequestException ex) {
                event.preventSave();
                notifications.create(ex.getMessage()).show();
            }
        }
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        Counterparty counterparty = getEditedEntity().getCounterparty();
        if (counterparty != null && (innLookupField.getValue() == null || innLookupField.getValue().isBlank())
                && counterparty.getInn() != null) {
            innLookupField.setValue(counterparty.getInn());
        }
        refreshWorkflowButtons();
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (getEditedEntity().getCounterparty() != null) {
            return;
        }
        String inn = innLookupField.getValue();
        if (inn == null || inn.isBlank()) {
            event.getErrors().add(messages.getMessage("com.company.money/paymentRequest.error.needInn"));
            return;
        }
        try {
            fillByInn(false);
        } catch (PaymentRequestException ex) {
            event.getErrors().add(ex.getMessage());
        }
    }

    @Subscribe("fillByInnButton")
    public void onFillByInnClick(final ClickEvent<JmixButton> event) {
        try {
            fillByInn();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
        }
    }

    @Subscribe("checkCounterpartyButton")
    public void onCheckCounterpartyClick(final ClickEvent<JmixButton> event) {
        try {
            PaymentRequest updated = paymentRequestService.refreshCounterpartyFromOneC(requireSavedId());
            applyWorkflowResult(updated);
            notifications.create(messageBundle.getMessage("checkCounterpartySuccess")).show();
        } catch (PaymentRequestException ex) {
            refreshFromDatabase();
            notifications.create(ex.getMessage()).show();
        }
    }

    @Subscribe("submitButton")
    public void onSubmitClick(final ClickEvent<JmixButton> event) {
        if (entityStates.isNew(getEditedEntity()) || hasUnsavedChanges()) {
            dialogs.createOptionDialog()
                    .withHeader(messageBundle.getMessage("saveBeforeSubmit.header"))
                    .withText(messageBundle.getMessage("saveBeforeSubmit.text"))
                    .withActions(
                            new DialogAction(DialogAction.Type.YES).withHandler(e -> saveThenSubmit()),
                            new DialogAction(DialogAction.Type.NO))
                    .open();
            return;
        }
        doSubmit();
    }

    @Subscribe("approveButton")
    public void onApproveClick(final ClickEvent<JmixButton> event) {
        PaymentRequest current = getEditedEntity();
        runWorkflow(() -> paymentRequestService.approve(
                requireSavedId(),
                current.getApprovedPaymentDate(),
                current.getCfoComment()));
    }

    @Subscribe("rejectButton")
    public void onRejectClick(final ClickEvent<JmixButton> event) {
        runWorkflow(() -> paymentRequestService.reject(requireSavedId(), getEditedEntity().getCfoComment()));
    }

    @Subscribe("cancelButton")
    public void onCancelClick(final ClickEvent<JmixButton> event) {
        runWorkflow(() -> paymentRequestService.cancel(requireSavedId()));
    }

    @Subscribe("deferButton")
    public void onDeferClick(final ClickEvent<JmixButton> event) {
        LocalDate defaultDate = getEditedEntity().getDeferredUntil();
        if (defaultDate == null) {
            defaultDate = LocalDate.now();
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("deferDialog.header"))
                .withParameters(InputParameter.localDateParameter("date")
                        .withLabel(messageBundle.getMessage("deferDialog.date"))
                        .withRequired(true)
                        .withDefaultValue(defaultDate))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        LocalDate date = closeEvent.getValue("date");
                        runWorkflow(() -> paymentRequestService.defer(requireSavedId(), date));
                    }
                })
                .open();
    }

    @Subscribe("exportButton")
    public void onExportClick(final ClickEvent<JmixButton> event) {
        openExportDialog();
    }

    @Subscribe("returnButton")
    public void onReturnClick(final ClickEvent<JmixButton> event) {
        openReturnDialog();
    }

    @Subscribe("openAttachmentButton")
    public void onOpenAttachmentClick(final ClickEvent<JmixButton> event) {
        PaymentRequestFile file = attachmentsDataGrid.getSingleSelectedItem();
        if (file == null || file.getContentFile() == null) {
            notifications.create(messageBundle.getMessage("needAttachmentSelection")).show();
            return;
        }
        downloader.download(file.getContentFile());
    }

    private void saveThenSubmit() {
        save().then(this::doSubmit);
    }

    private void doSubmit() {
        runWorkflow(() -> paymentRequestService.submit(requireSavedId()));
    }

    private void attachUploadedFile() {
        FileRef fileRef = attachFileField.getValue();
        if (fileRef == null) {
            return;
        }
        PaymentRequestFile file = dataContext.create(PaymentRequestFile.class);
        file.setPaymentRequest(getEditedEntity());
        file.setContentFile(fileRef);
        file.setDescription(fileRef.getFileName());
        attachmentsDc.getMutableItems().add(file);
        attachFileField.setValue(null);
    }

    private Stream<Counterparty> fetchCounterparties(Query<Counterparty, String> query) {
        try {
            return oneCCatalogSearchService
                    .searchCounterparties(query.getFilter().orElse(""), query.getOffset(), query.getLimit())
                    .stream();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            return Stream.empty();
        }
    }

    private Stream<Nomenclature> fetchNomenclature(Query<Nomenclature, String> query) {
        try {
            return oneCCatalogSearchService
                    .searchNomenclature(query.getFilter().orElse(""), query.getOffset(), query.getLimit())
                    .stream();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            return Stream.empty();
        }
    }

    private Stream<CounterpartyBankAccount> fetchBankAccounts(Query<CounterpartyBankAccount, String> query) {
        Counterparty counterparty = getEditedEntity().getCounterparty();
        if (counterparty == null) {
            return Stream.empty();
        }
        try {
            return oneCCatalogSearchService
                    .searchBankAccounts(counterparty, query.getFilter().orElse(""), query.getOffset(), query.getLimit())
                    .stream();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            return Stream.empty();
        }
    }

    private Stream<CounterpartyContract> fetchContracts(Query<CounterpartyContract, String> query) {
        Counterparty counterparty = getEditedEntity().getCounterparty();
        if (counterparty == null) {
            return Stream.empty();
        }
        try {
            return oneCCatalogSearchService
                    .searchContracts(counterparty, query.getFilter().orElse(""), query.getOffset(), query.getLimit())
                    .stream();
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
            return Stream.empty();
        }
    }

    private void fillByInn() {
        fillByInn(true);
    }

    private void fillByInn(boolean notify) {
        String inn = innLookupField.getValue();
        OneCCatalogSearchService.InnResolveResult resolved = oneCCatalogSearchService.resolveByInn(inn);
        applyCounterparty(resolved.counterparty());
        getEditedEntity().setMissingInOneC(!resolved.foundInOneC());
        if (resolved.foundInOneC()) {
            applyMainContract(resolved.counterparty());
            List<CounterpartyBankAccount> accounts = oneCCatalogSearchService
                    .searchBankAccounts(resolved.counterparty(), "", 0, 1);
            if (!accounts.isEmpty()) {
                CounterpartyBankAccount account = accounts.getFirst();
                getEditedEntity().setCounterpartyBankAccount(account);
                bankAccountField.setValue(account);
            }
            if (notify) {
                notifications.create(messageBundle.getMessage("fillByInnSuccess")).show();
            }
        } else {
            getEditedEntity().setCounterpartyBankAccount(null);
            bankAccountField.setValue(null);
            getEditedEntity().setContract(null);
            contractField.setValue(null);
            if (notify) {
                notifications.create(messageBundle.getMessage("fillByInnPending")).show();
            }
        }
        refreshMissingCounterpartyUi();
    }

    private void applyCounterparty(Counterparty counterparty) {
        getEditedEntity().setCounterparty(counterparty);
        counterpartyField.setValue(counterparty);
        if (counterparty != null && counterparty.getInn() != null) {
            innLookupField.setValue(counterparty.getInn());
        }
        if (bankAccountField.getDataProvider() != null) {
            bankAccountField.getDataProvider().refreshAll();
        }
        if (contractField.getDataProvider() != null) {
            contractField.getDataProvider().refreshAll();
        }
    }

    private void refreshMissingCounterpartyUi() {
        boolean missing = getEditedEntity().isMissingInOneC();
        boolean financialDirector = paymentRequestAccess.isFinancialDirector();
        PaymentRequestStatus status = getEditedEntity().getStatus();
        boolean draft = status == PaymentRequestStatus.DRAFT;
        boolean pending = status == PaymentRequestStatus.SUBMITTED || status == PaymentRequestStatus.DEFERRED;

        innLookupField.setVisible(true);
        innLookupField.setReadOnly(!draft && !financialDirector);
        fillByInnButton.setVisible(draft);
        fillByInnButton.setEnabled(draft);
        checkCounterpartyButton.setVisible(financialDirector && missing);
        checkCounterpartyButton.setEnabled(financialDirector && missing && !entityStates.isNew(getEditedEntity())
                && (pending || draft));
        fdMissingNotice.setVisible(financialDirector && missing);
        fdMissingNotice.setText(messageBundle.getMessage("fdMissingCounterpartyNotice"));
        bankAccountField.setRequired(!missing);
        requestedPaymentDateField.setRequired(missing);

        toggleMissingHighlight(counterpartyField, missing);
        toggleMissingHighlight(bankAccountField, missing);
        toggleMissingHighlight(contractField, missing);
    }

    private void toggleMissingHighlight(com.vaadin.flow.component.HasStyle component, boolean missing) {
        if (missing) {
            component.addClassName("pr-missing-1c-field");
        } else {
            component.removeClassName("pr-missing-1c-field");
        }
    }

    private void applyMainContract(Counterparty counterparty) {
        getEditedEntity().setContract(null);
        contractField.setValue(null);
        if (contractField.getDataProvider() != null) {
            contractField.getDataProvider().refreshAll();
        }
        if (counterparty == null) {
            return;
        }
        try {
            CounterpartyContract main = oneCCatalogSearchService.findMainContract(counterparty);
            if (main != null) {
                getEditedEntity().setContract(main);
                contractField.setValue(main);
            }
        } catch (PaymentRequestException ex) {
            notifications.create(ex.getMessage()).show();
        }
    }

    private void openExportDialog() {
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("exportDialog.header"))
                .withText(messageBundle.getMessage("exportDialog.text"))
                .withActions(
                        new DialogAction(DialogAction.Type.OK)
                                .withText(messageBundle.getMessage("exportDialog.prepare"))
                                .withHandler(e -> runWorkflow(() -> paymentRequestService.exportToOneC(
                                        requireSavedId(), OneCExportMode.PREPARE))),
                        new DialogAction(DialogAction.Type.YES)
                                .withText(messageBundle.getMessage("exportDialog.approve"))
                                .withHandler(e -> runWorkflow(() -> paymentRequestService.exportToOneC(
                                        requireSavedId(), OneCExportMode.APPROVE))),
                        new DialogAction(DialogAction.Type.CANCEL))
                .open();
    }

    private void openReturnDialog() {
        dialogs.createOptionDialog()
                .withHeader(messageBundle.getMessage("returnDialog.header"))
                .withText(messageBundle.getMessage("returnDialog.text"))
                .withActions(
                        new DialogAction(DialogAction.Type.OK)
                                .withText(messageBundle.getMessage("returnDialog.revision"))
                                .withHandler(e -> runWorkflow(() -> paymentRequestService.returnFromOneC(
                                        requireSavedId(), OneCReturnAction.REVISION, null,
                                        getEditedEntity().getCfoComment()))),
                        new DialogAction(DialogAction.Type.YES)
                                .withText(messageBundle.getMessage("returnDialog.defer"))
                                .withHandler(e -> openReturnDeferDialog()),
                        new DialogAction(DialogAction.Type.NO)
                                .withText(messageBundle.getMessage("returnDialog.reject"))
                                .withHandler(e -> runWorkflow(() -> paymentRequestService.returnFromOneC(
                                        requireSavedId(), OneCReturnAction.REJECT, null,
                                        getEditedEntity().getCfoComment()))),
                        new DialogAction(DialogAction.Type.CANCEL))
                .open();
    }

    private void openReturnDeferDialog() {
        LocalDate defaultDate = getEditedEntity().getDeferredUntil();
        if (defaultDate == null) {
            defaultDate = LocalDate.now();
        }
        dialogs.createInputDialog(this)
                .withHeader(messageBundle.getMessage("deferDialog.header"))
                .withParameters(InputParameter.localDateParameter("date")
                        .withLabel(messageBundle.getMessage("deferDialog.date"))
                        .withRequired(true)
                        .withDefaultValue(defaultDate))
                .withActions(DialogActions.OK_CANCEL)
                .withCloseListener(closeEvent -> {
                    if (closeEvent.closedWith(DialogOutcome.OK)) {
                        LocalDate date = closeEvent.getValue("date");
                        runWorkflow(() -> paymentRequestService.returnFromOneC(
                                requireSavedId(), OneCReturnAction.DEFER, date,
                                getEditedEntity().getCfoComment()));
                    }
                })
                .open();
    }

    private void runWorkflow(WorkflowAction action) {
        try {
            PaymentRequest updated = action.run();
            applyWorkflowResult(updated);
            notifications.create(messageBundle.getMessage("workflowSuccess"))
                    .show();
        } catch (PaymentRequestException ex) {
            refreshFromDatabase();
            notifications.create(ex.getMessage()).show();
        }
    }

    private void applyWorkflowResult(PaymentRequest updated) {
        getEditedEntity().setVersion(updated.getVersion());
        getEditedEntity().setStatus(updated.getStatus());
        getEditedEntity().setRequestNumber(updated.getRequestNumber());
        getEditedEntity().setApprovedPaymentDate(updated.getApprovedPaymentDate());
        getEditedEntity().setDeferredUntil(updated.getDeferredUntil());
        getEditedEntity().setCfoComment(updated.getCfoComment());
        getEditedEntity().setOneCExportPayload(updated.getOneCExportPayload());
        getEditedEntity().setOneCDocumentRef(updated.getOneCDocumentRef());
        getEditedEntity().setOneCDocumentNumber(updated.getOneCDocumentNumber());
        getEditedEntity().setOneCDocumentDate(updated.getOneCDocumentDate());
        if (entityStates.isLoaded(updated, "lastChangedBy")) {
            getEditedEntity().setLastChangedBy(updated.getLastChangedBy());
        }
        if (entityStates.isLoaded(updated, "counterparty")) {
            getEditedEntity().setCounterparty(updated.getCounterparty());
            counterpartyField.setValue(updated.getCounterparty());
        }
        if (entityStates.isLoaded(updated, "counterpartyBankAccount")) {
            getEditedEntity().setCounterpartyBankAccount(updated.getCounterpartyBankAccount());
            bankAccountField.setValue(updated.getCounterpartyBankAccount());
        }
        if (entityStates.isLoaded(updated, "contract")) {
            getEditedEntity().setContract(updated.getContract());
            contractField.setValue(updated.getContract());
        }
        getEditedEntity().setMissingInOneC(updated.getMissingInOneC());
        if (entityStates.isLoaded(updated, "counterparty")
                && updated.getCounterparty() != null
                && entityStates.isLoaded(updated.getCounterparty(), "inn")
                && updated.getCounterparty().getInn() != null) {
            innLookupField.setValue(updated.getCounterparty().getInn());
        }
        clearChanges();
        refreshWorkflowButtons();
    }

    private void refreshFromDatabase() {
        if (entityStates.isNew(getEditedEntity()) || getEditedEntity().getId() == null) {
            return;
        }
        try {
            applyWorkflowResult(paymentRequestService.loadForWorkflow(getEditedEntity().getId()));
        } catch (RuntimeException ignored) {
        }
    }

    private User currentPortalUser() {
        UserDetails userDetails = currentAuthentication.getUser();
        if (userDetails instanceof User user) {
            return dataManager.load(User.class).id(user.getId()).one();
        }
        return dataManager.load(User.class)
                .query("select e from vshd1_User e where e.username = :username")
                .parameter("username", userDetails.getUsername())
                .optional()
                .orElse(null);
    }

    private java.util.UUID requireSavedId() {
        if (entityStates.isNew(getEditedEntity())) {
            throw new PaymentRequestException(messageBundle.getMessage("saveBeforeWorkflow"));
        }
        return getEditedEntity().getId();
    }

    private void refreshWorkflowButtons() {
        PaymentRequestStatus status = getEditedEntity().getStatus();
        boolean employee = paymentRequestAccess.isEmployeeActor();
        boolean financialDirector = paymentRequestAccess.isFinancialDirector();
        boolean draft = status == PaymentRequestStatus.DRAFT;
        boolean pending = status == PaymentRequestStatus.SUBMITTED || status == PaymentRequestStatus.DEFERRED;

        submitButton.setVisible(employee);
        cancelButton.setVisible(employee);
        approveButton.setVisible(financialDirector);
        rejectButton.setVisible(financialDirector);
        deferButton.setVisible(financialDirector);
        exportButton.setVisible(financialDirector);
        returnButton.setVisible(financialDirector);

        submitButton.setEnabled(employee && draft);
        cancelButton.setEnabled(employee && (draft || pending));
        approveButton.setEnabled(financialDirector && pending && !getEditedEntity().isMissingInOneC());
        rejectButton.setEnabled(financialDirector && pending);
        deferButton.setEnabled(financialDirector && pending);
        exportButton.setEnabled(financialDirector && canExport(getEditedEntity()));
        returnButton.setEnabled(financialDirector && PaymentRequestService.canReturn(getEditedEntity()));
        refreshMissingCounterpartyUi();
    }

    private boolean canExport(PaymentRequest request) {
        return request.getStatus() == PaymentRequestStatus.APPROVED;
    }

    @FunctionalInterface
    private interface WorkflowAction {
        PaymentRequest run();
    }
}

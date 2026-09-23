package com.company.money.payment;

import com.company.money.MONEYApplication;
import com.company.money.test_support.AuthenticatedAsAdmin;
import com.company.money.view.paymentrequest.PaymentRequestDetailView;
import com.company.money.view.paymentrequest.PaymentRequestListView;
import com.vaadin.flow.component.Component;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.datepicker.TypedDatePicker;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileStorageUploadField;
import io.jmix.flowui.component.valuepicker.EntityPicker;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@UiTest
@SpringBootTest(classes = {MONEYApplication.class, FlowuiTestAssistConfiguration.class})
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class PaymentRequestDetailViewUiTest {

    @Autowired
    ViewNavigators viewNavigators;

    @Test
    void opensDetailWithOneCDocumentRefField() {
        viewNavigators.view(UiTestUtils.getCurrentView(), PaymentRequestListView.class).navigate();
        PaymentRequestListView listView = UiTestUtils.getCurrentView();
        JmixButton listApproveButton = UiTestUtils.getComponent(listView, "approveButton");
        JmixButton listDeferButton = UiTestUtils.getComponent(listView, "deferButton");
        JmixButton listExportButton = UiTestUtils.getComponent(listView, "exportButton");
        JmixButton listReturnButton = UiTestUtils.getComponent(listView, "returnButton");
        assertThat(listApproveButton.isVisible()).isTrue();
        assertThat(listDeferButton.isVisible()).isTrue();
        assertThat(listExportButton.isVisible()).isTrue();
        assertThat(listReturnButton.isVisible()).isTrue();
        DataGrid<?> listGrid = UiTestUtils.getComponent(listView, "paymentRequestsDataGrid");
        assertThat(listGrid.getColumnByKey("counterpartyBankAccount")).isNull();
        assertThat(listGrid.getColumnByKey("contract")).isNull();
        assertThat(listGrid.getColumnByKey("approvedPaymentDate")).isNotNull();
        assertThat(listGrid.getColumnByKey("editorActions")).isNotNull();
        Component statusLegend = UiTestUtils.getComponent(listView, "statusLegend");
        assertThat(statusLegend.isVisible()).isTrue();

        JmixButton createButton = UiTestUtils.getComponent(listView, "createButton");
        createButton.click();

        PaymentRequestDetailView detailView = UiTestUtils.getCurrentView();
        TypedTextField<String> requestNumberField = UiTestUtils.getComponent(detailView, "requestNumberField");
        TypedTextField<String> refField = UiTestUtils.getComponent(detailView, "oneCDocumentField");
        EntityPicker<?> applicantField = UiTestUtils.getComponent(detailView, "applicantField");
        EntityPicker<?> lastChangedByField = UiTestUtils.getComponent(detailView, "lastChangedByField");
        JmixButton exportButton = UiTestUtils.getComponent(detailView, "exportButton");
        JmixButton returnButton = UiTestUtils.getComponent(detailView, "returnButton");
        EntityComboBox<?> bankAccountField = UiTestUtils.getComponent(detailView, "bankAccountField");
        EntityComboBox<?> contractField = UiTestUtils.getComponent(detailView, "contractField");
        FileStorageUploadField attachFileField = UiTestUtils.getComponent(detailView, "attachFileField");
        JmixButton saveButton = UiTestUtils.getComponent(detailView, "saveButton");
        JmixButton cancelButton = UiTestUtils.getComponent(detailView, "cancelButton");
        JmixButton deferButton = UiTestUtils.getComponent(detailView, "deferButton");
        JmixButton openAttachmentButton = UiTestUtils.getComponent(detailView, "openAttachmentButton");
        TypedDatePicker<?> deferredUntilField = UiTestUtils.getComponent(detailView, "deferredUntilField");
        TypedTextField<String> innLookupField = UiTestUtils.getComponent(detailView, "innLookupField");
        JmixButton fillByInnButton = UiTestUtils.getComponent(detailView, "fillByInnButton");
        JmixButton checkCounterpartyButton = UiTestUtils.getComponent(detailView, "checkCounterpartyButton");

        assertThat(requestNumberField.isReadOnly()).isTrue();
        assertThat(refField.isReadOnly()).isTrue();
        assertThat(applicantField.isReadOnly()).isTrue();
        assertThat(lastChangedByField.isReadOnly()).isTrue();
        assertThat(deferredUntilField.isReadOnly()).isTrue();
        assertThat(exportButton).isNotNull();
        assertThat(returnButton).isNotNull();
        assertThat(bankAccountField).isNotNull();
        assertThat(contractField).isNotNull();
        assertThat(attachFileField).isNotNull();
        assertThat(saveButton).isNotNull();
        assertThat(cancelButton).isNotNull();
        assertThat(deferButton).isNotNull();
        assertThat(openAttachmentButton).isNotNull();
        assertThat(innLookupField).isNotNull();
        assertThat(innLookupField.isVisible()).isTrue();
        assertThat(fillByInnButton.isVisible()).isTrue();
        assertThat(fillByInnButton).isNotNull();
        assertThat(checkCounterpartyButton).isNotNull();
    }
}

package com.company.money.view.paymentrequestfile;

import com.company.money.entity.PaymentRequestFile;
import com.company.money.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "payment-request-files/:id", layout = MainView.class)
@ViewController(id = "vshd1_PaymentRequestFile.detail")
@ViewDescriptor(path = "payment-request-file-detail-view.xml")
@EditedEntityContainer("paymentRequestFileDc")
@DialogMode(width = "40em")
public class PaymentRequestFileDetailView extends StandardDetailView<PaymentRequestFile> {
}

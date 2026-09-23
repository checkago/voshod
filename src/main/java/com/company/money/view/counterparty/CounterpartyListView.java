package com.company.money.view.counterparty;

import com.company.money.entity.Counterparty;
import com.company.money.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "counterparties", layout = MainView.class)
@ViewController(id = "vshd1_Counterparty.list")
@ViewDescriptor(path = "counterparty-list-view.xml")
@LookupComponent("counterpartiesDataGrid")
@DialogMode(width = "64em")
public class CounterpartyListView extends StandardListView<Counterparty> {
}

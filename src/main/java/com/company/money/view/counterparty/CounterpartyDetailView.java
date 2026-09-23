package com.company.money.view.counterparty;

import com.company.money.entity.Counterparty;
import com.company.money.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import java.util.UUID;

@Route(value = "counterparties/:id", layout = MainView.class)
@ViewController(id = "vshd1_Counterparty.detail")
@ViewDescriptor(path = "counterparty-detail-view.xml")
@EditedEntityContainer("counterpartyDc")
public class CounterpartyDetailView extends StandardDetailView<Counterparty> {

    @Subscribe
    public void onInitEntity(final InitEntityEvent<Counterparty> event) {
        if (event.getEntity().getOneCRef() == null) {
            event.getEntity().setOneCRef(UUID.randomUUID());
        }
    }
}

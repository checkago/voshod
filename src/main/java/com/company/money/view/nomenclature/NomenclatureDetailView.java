package com.company.money.view.nomenclature;

import com.company.money.entity.Nomenclature;
import com.company.money.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

import java.util.UUID;

@Route(value = "nomenclatures/:id", layout = MainView.class)
@ViewController(id = "vshd1_Nomenclature.detail")
@ViewDescriptor(path = "nomenclature-detail-view.xml")
@EditedEntityContainer("nomenclatureDc")
public class NomenclatureDetailView extends StandardDetailView<Nomenclature> {

    @Subscribe
    public void onInitEntity(final InitEntityEvent<Nomenclature> event) {
        if (event.getEntity().getOneCRef() == null) {
            event.getEntity().setOneCRef(UUID.randomUUID());
        }
    }
}

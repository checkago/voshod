package com.company.money.view.nomenclature;

import com.company.money.entity.Nomenclature;
import com.company.money.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "nomenclatures", layout = MainView.class)
@ViewController(id = "vshd1_Nomenclature.list")
@ViewDescriptor(path = "nomenclature-list-view.xml")
@LookupComponent("nomenclaturesDataGrid")
@DialogMode(width = "64em")
public class NomenclatureListView extends StandardListView<Nomenclature> {
}

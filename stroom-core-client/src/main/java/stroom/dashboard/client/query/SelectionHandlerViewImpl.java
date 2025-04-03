/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.query.SelectionHandlerPresenter.SelectionHandlerView;
import stroom.docref.HasDisplayValue;
import stroom.item.client.SelectionBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.stream.Collectors;

public class SelectionHandlerViewImpl
        extends ViewWithUiHandlers<SelectionHandlerUiHandlers>
        implements SelectionHandlerView {

    private static final HasDisplayValue ANY = () -> "Any";

    private final Widget widget;

    @UiField
    SimplePanel layout;
    @UiField
    SelectionBox<HasDisplayValue> component;
    @UiField
    CustomCheckBox enabled;
    @UiField
    SimplePanel currentSelection;

    @Inject
    public SelectionHandlerViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        component.focus();
    }

    @Override
    public void setComponentList(final List<Component> componentList) {
        final HasDisplayValue component = this.component.getValue();

        this.component.clear();
        this.component.addItem(ANY);

        final List<HasDisplayValue> newList = componentList.stream()
                .map(e ->
                        (HasDisplayValue) e)
                .collect(Collectors.toList());
        this.component.addItems(newList);

        // Reselect component id.
        this.component.setValue(component);
    }

    @Override
    public Component getComponent() {
        if (ANY.equals(this.component.getValue())) {
            return null;
        }
        return (Component) this.component.getValue();
    }

    @Override
    public void setComponent(final Component component) {
        if (component == null) {
            this.component.setValue(ANY);
        } else {
            this.component.setValue(component);
        }
    }

    @Override
    public void setExpressionView(final View view) {
        layout.add(view.asWidget());
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public void setCurrentSelection(final View view) {
        this.currentSelection.setWidget(view.asWidget());
    }

    @UiHandler("component")
    public void onComponentValueChange(final ValueChangeEvent<HasDisplayValue> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onComponentChange();
        }
    }

    public interface Binder extends UiBinder<Widget, SelectionHandlerViewImpl> {

    }
}

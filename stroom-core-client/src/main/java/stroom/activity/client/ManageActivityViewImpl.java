/*
 * Copyright 2016-2025 Crown Copyright
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
 */

package stroom.activity.client;

import stroom.activity.client.ManageActivityPresenter.ManageActivityView;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;


public class ManageActivityViewImpl extends ViewWithUiHandlers<ManageActivityUiHandlers> implements ManageActivityView {

    public interface Binder extends UiBinder<Widget, ManageActivityViewImpl> {

    }

    private final Widget widget;

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel dataGrid;

    private Supplier<SafeHtml> tooltipContentSupplier;

    @Inject
    public ManageActivityViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);

        nameFilter.registerPopupTextProvider(this::getTooltipContent);
    }


    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ManageActivityPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    @Override
    public void focus() {
        nameFilter.forceFocus();
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @Override
    public void setTooltipContentSupplier(final Supplier<SafeHtml> tooltipContentSupplier) {
        this.tooltipContentSupplier = tooltipContentSupplier;
    }

    private SafeHtml getTooltipContent() {
        return tooltipContentSupplier.get();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }
}

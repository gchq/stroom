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

package stroom.widget.dropdowntree.client.view;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.function.Supplier;

public class ExplorerPopupViewImpl extends ViewWithUiHandlers<ExplorerPopupUiHandlers> implements ExplorerPopupView {

    private final Widget widget;
    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel treeContainer;

    @Inject
    public ExplorerPopupViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.sinkEvents(Event.ONMOUSEUP | Event.FOCUSEVENTS);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        nameFilter.forceFocus();
    }

    @Override
    public void setCellTree(final Widget widget) {
        treeContainer.setWidget(widget);
    }

    @Override
    public void setQuickFilterTooltipSupplier(final Supplier<SafeHtml> tooltipSupplier) {
        nameFilter.registerPopupTextProvider(tooltipSupplier);
    }

    @UiHandler("nameFilter")
    public void onNameFilterChange(final ValueChangeEvent<String> event) {
//        GWT.log("onNameFilterChange: " + event);
        if (getUiHandlers() != null) {
            getUiHandlers().nameFilterChanged(nameFilter.getText());
        }
    }

    @Override
    public void clearQuickFilter() {
        nameFilter.clear();
    }

    @Override
    public void setQuickFilter(final String filterInput) {
        nameFilter.setText(filterInput);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ExplorerPopupViewImpl> {

    }
}

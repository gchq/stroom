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

package stroom.importexport.client.view;

import stroom.importexport.client.presenter.ExportConfigPresenter.ExportConfigView;
import stroom.importexport.client.presenter.ExportConfigUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.MaxScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ExportConfigViewImpl
        extends ViewWithUiHandlers<ExportConfigUiHandlers>
        implements ExportConfigView {

    private final Widget widget;
    @UiField
    QuickFilter nameFilter;
    @UiField
    MaxScrollPanel tree;
    @UiField
    FlowPanel buttonContainer;

    private boolean hasActiveFilter = false;

    @Inject
    public ExportConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
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
    public void setTreeView(final View view) {
        tree.setWidget(view.asWidget());
    }

    @Override
    public FlowPanel getButtonContainer() {
        return buttonContainer;
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ExportConfigViewImpl> {

    }
}

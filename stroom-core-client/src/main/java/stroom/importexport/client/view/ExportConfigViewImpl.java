/*
 * Copyright 2016 Crown Copyright
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
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.SvgButton;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
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
    SimplePanel tree;
    @UiField(provided = true)
    SvgButton typeFilter;

    @Inject
    public ExportConfigViewImpl(final Binder binder) {
        typeFilter = SvgButton.create(SvgPresets.FILTER);
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTreeView(final View view) {
        view.asWidget().setWidth("100%");
        view.asWidget().setHeight("100%");
        tree.setWidget(view.asWidget());
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }

    @UiHandler("typeFilter")
    void onFilterClick(final MouseDownEvent event) {
        getUiHandlers().showTypeFilter(event);
    }

    public interface Binder extends UiBinder<Widget, ExportConfigViewImpl> {

    }
}

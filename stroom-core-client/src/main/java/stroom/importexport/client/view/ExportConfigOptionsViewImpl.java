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

import stroom.importexport.client.presenter.ExportConfigOptionsPresenter.ExportConfigOptionsView;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ExportConfigOptionsViewImpl
        extends ViewImpl
        implements ExportConfigOptionsView {

    private final Widget widget;

    @UiField
    CustomCheckBox includeProcFiltersCheckBox;
    @UiField
    SimplePanel singletonDocsPanel;

    @Inject
    public ExportConfigOptionsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        includeProcFiltersCheckBox.setValue(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public boolean isIncludeProcFilters() {
        return includeProcFiltersCheckBox.getValue();
    }

    @Override
    public void setIncludeProcFilters(final boolean isIncludeProcFilters) {
        includeProcFiltersCheckBox.setValue(isIncludeProcFilters);
    }

    @Override
    public void setSingletonDocsPicker(final Widget widget) {
        singletonDocsPanel.setWidget(widget);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ExportConfigOptionsViewImpl> {

    }
}

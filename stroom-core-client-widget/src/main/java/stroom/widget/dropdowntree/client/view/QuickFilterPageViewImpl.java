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

import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Objects;
import java.util.function.Supplier;

public class QuickFilterPageViewImpl extends ViewWithUiHandlers<QuickFilterUiHandlers>
        implements QuickFilterPageView {

    @UiField
    FormGroup formGroup;
    @UiField
    QuickFilter quickFilter;
    @UiField
    SimplePanel data;

    private final Widget widget;

    @Inject
    public QuickFilterPageViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        formGroup.setLabel(null);
    }

    @Override
    public void setLabel(final String label) {
        formGroup.setLabel(label);
    }

    @Override
    public void setHelpText(final SafeHtml helpText) {
        formGroup.overrideHelpText(helpText);
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        quickFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @Override
    public void focus() {
        quickFilter.forceFocus();
    }

    @Override
    public void setDataView(final View view) {
        data.setWidget(view.asWidget());
    }

    @Override
    public void setQuickFilterText(final String quickFilterText) {
        final String currVal = quickFilter.getText();
        quickFilter.setText(quickFilterText);
        if (!Objects.equals(currVal, quickFilterText)) {
            getUiHandlers().onFilterChange(quickFilterText);
        }
    }

    @UiHandler("quickFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().onFilterChange(quickFilter.getText());
    }


    @Override
    public Widget asWidget() {
        return widget;
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, QuickFilterPageViewImpl> {

    }
}

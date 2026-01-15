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

package stroom.explorer.client.view;

import stroom.explorer.client.presenter.AbstractFindPresenter;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class FindViewImpl
        extends ViewWithUiHandlers<FindUiHandlers>
        implements AbstractFindPresenter.FindView {

    private final Widget widget;

    @UiField
    FlowPanel topPanel;
    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel bottomPanel;

    @Inject
    public FindViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setDialogMode(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setResultView(final View view) {
        bottomPanel.setWidget(view.asWidget());
    }

    @Override
    public void focus() {
        nameFilter.forceFocus();
    }

    @Override
    public void setDialogMode(final boolean dialog) {
        if (dialog) {
            widget.addStyleName("FindViewImpl--dialog");
        } else {
            widget.removeStyleName("FindViewImpl--dialog");
        }
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeQuickFilter(nameFilter.getText());
    }

    @UiHandler("nameFilter")
    void onFilterKeyDown(final KeyDownEvent event) {
        getUiHandlers().onFilterKeyDown(event);
    }

    public interface Binder extends UiBinder<Widget, FindViewImpl> {

    }
}

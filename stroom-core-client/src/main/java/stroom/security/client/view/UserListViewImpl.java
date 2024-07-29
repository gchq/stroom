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

package stroom.security.client.view;

import stroom.security.client.presenter.UserListUiHandlers;
import stroom.security.client.presenter.UserListView;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.QuickFilter;

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

import java.util.function.Supplier;

public class UserListViewImpl extends ViewWithUiHandlers<UserListUiHandlers> implements UserListView {

    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel dataGrid;
    private final Widget widget;

    @Inject
    public UserListViewImpl(final Binder binder,
                            final UiConfigCache uiConfigCache) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        nameFilter.registerPopupTextProvider(popupTextSupplier);
    }

    @Override
    public void focus() {
        nameFilter.forceFocus();
    }

    @Override
    public void setDatGridView(View view) {
        dataGrid.setWidget(view.asWidget());
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, UserListViewImpl> {

    }
}

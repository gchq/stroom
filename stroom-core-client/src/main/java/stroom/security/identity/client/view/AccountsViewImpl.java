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

package stroom.security.identity.client.view;

import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class AccountsViewImpl
        extends ViewImpl
        implements AccountsView {

    private final Widget widget;

    //    @UiField
//    QuickFilter quickFilter;
    @UiField
    SimplePanel listContainer;

    @Inject
    public AccountsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
    }

//    @Override
//    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
//        quickFilter.registerPopupTextProvider(popupTextSupplier);
//    }

    @Override
    public void focus() {

    }

    @Override
    public void setList(final Widget widget) {
        listContainer.setWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

//    @UiHandler("quickFilter")
//    void onFilterChange(final ValueChangeEvent<String> event) {
//        getUiHandlers().changeQuickFilterInput(quickFilter.getText());
//    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AccountsViewImpl> {

    }
}

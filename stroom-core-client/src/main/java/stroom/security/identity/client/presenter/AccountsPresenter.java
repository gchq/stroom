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

package stroom.security.identity.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.security.identity.client.presenter.AccountsPresenter.AccountsView;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class AccountsPresenter
        extends ContentTabPresenter<AccountsView>
        implements Refreshable {

    public static final String TAB_TYPE = "Accounts";
    private final AccountsListPresenter listPresenter;

    @Inject
    public AccountsPresenter(final EventBus eventBus,
                             final AccountsView view,
                             final AccountsListPresenter listPresenter,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        view.setList(listPresenter.getWidget());
    }

    public void focus() {
        getView().focus();
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.USER;
    }

    @Override
    public String getLabel() {
        return "Manage Accounts";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    public void setFilterInput(final String filterInput) {
        listPresenter.setQuickFilterText(filterInput);
    }


    // --------------------------------------------------------------------------------


    public interface AccountsView extends View {

        void focus();

        void setList(Widget widget);
    }
}

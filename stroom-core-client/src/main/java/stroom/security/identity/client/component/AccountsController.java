/*
 * Copyright 2023 Crown Copyright
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

package stroom.security.identity.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.util.client.HasRefresh;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the accounts of Stroom's internal identity provider.
public class AccountsController extends ContentTabController
        implements HasRefresh {

    public static final String TAB_TYPE = "Accounts";

    private final AccountsListController accountsList;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus      The application's event bus.
    /// @param accountsList  The accounts list.
    /// @param uiConfigCache The UI's configuration.
    @Inject
    public AccountsController(final EventBus eventBus,
                              final AccountsListController accountsList,
                              final UiConfigCache uiConfigCache) {
        super(eventBus);
        own(accountsList);
        this.accountsList = accountsList;
        initWidget(accountsList.asWidget());
    }

    @Override
    public void refresh() {
        accountsList.refresh();
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

    /// Filters the accounts.
    ///
    /// @param filterInput The filter.
    public void setFilterInput(final String filterInput) {
        accountsList.setQuickFilterText(filterInput);
    }
}

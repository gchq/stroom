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

package stroom.security.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.HashedApiKey;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.IsButton;
import stroom.widget.util.client.HasRefresh;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Shows the API keys, and creates, edits and deletes them.
public class ApiKeysController extends ContentTabController
        implements HasRefresh {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);
    public static final String TAB_TYPE = "ApiKeys";

    private final ApiKeysListController apiKeysList;
    private final Provider<ApiKeyEditorController> editApiKeyProvider;
    private final RestFactory restFactory;
    private final IsButton addButton;
    private final IsButton editButton;
    private final IsButton deleteButton;
    private UserRef owner = null;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus           The application's event bus.
    /// @param apiKeysList        The API keys list.
    /// @param restFactory        Makes REST requests.
    /// @param uiConfigCache      The UI's configuration.
    /// @param editApiKeyProvider Makes the edit API key.
    @Inject
    public ApiKeysController(final EventBus eventBus,
                             final ApiKeysListController apiKeysList,
                             final RestFactory restFactory,
                             final UiConfigCache uiConfigCache,
                             final Provider<ApiKeyEditorController> editApiKeyProvider) {
        super(eventBus);
        own(apiKeysList);
        this.apiKeysList = apiKeysList;
        this.restFactory = restFactory;
        this.editApiKeyProvider = editApiKeyProvider;

        initWidget(apiKeysList.asWidget());

        apiKeysList.getToolbar().setName("API key actions");
        addButton = apiKeysList.getToolbar().button(SvgImage.ADD, "New API Key", this::createNewKey);
        editButton = apiKeysList.getToolbar().button(SvgImage.EDIT, "Edit Selected API Key",
                this::editSelectedKey);
        editButton.setEnabled(false);
        deleteButton = apiKeysList.getToolbar().button(SvgImage.DELETE, "Delete Selected API Key",
                this::deleteSelectedKeys);
        deleteButton.setEnabled(false);

        own(getSelectionModel().addSelectionHandler(e -> {
            onSelection();
            if (e.getSelectionType().isDoubleSelect()
                && NullSafe.hasOneItem(getSelectedItems())) {
                editSelectedKey();
            }
        }));
    }

    private void setButtonStates() {
        final int selectedCount = NullSafe.size(getSelectedItems());
        editButton.setEnabled(selectedCount == 1);
        deleteButton.setEnabled(selectedCount > 0);
        if (selectedCount > 1) {
            deleteButton.setTitle("Delete " + selectedCount + " Selected API Keys");
        } else {
            deleteButton.setTitle("Delete Selected API Key");
        }
    }

    private void onSelection() {
        setButtonStates();
    }

    private List<HashedApiKey> getSelectedItems() {
        return apiKeysList.getSelectionModel().getSelectedItems();
    }

    private MultiSelectionModelImpl<HashedApiKey> getSelectionModel() {
        return apiKeysList.getSelectionModel();
    }

    private void createNewKey() {
        final boolean allowOwnerSelection = owner == null;
        editApiKeyProvider.get().showCreateDialog(
                this,
                ApiKeyEditorMode.PRE_CREATE,
                apiKeysList::refresh,
                allowOwnerSelection);
    }

    private void editSelectedKey() {
        final HashedApiKey apiKey = getSelectionModel().getSelected();
        editApiKeyProvider.get().showEditDialog(this, apiKey, ApiKeyEditorMode.EDIT, apiKeysList::refresh);
    }

    private void deleteSelectedKeys() {

        final List<HashedApiKey> selectedItems = getSelectedItems();

        final Runnable onSuccess = () -> {
            getSelectionModel().clear();
            refresh();
        };

        final RestErrorHandler onFailure = restError -> {
            // Something went wrong so refresh the data.
            AlertEvent.fireError(this, restError.getMessage(), null);
            refresh();
        };

        final int cnt = selectedItems.size();
        if (cnt == 1) {
            deleteSingle(selectedItems, onSuccess, onFailure);
        } else if (cnt > 1) {
            deleteMultiple(selectedItems, onSuccess, onFailure);
        }
    }

    private void deleteMultiple(final List<HashedApiKey> selectedItems,
                                final Runnable onSuccess,
                                final RestErrorHandler onFailure) {
        final Set<Integer> ids = selectedItems.stream()
                .map(HashedApiKey::getId)
                .collect(Collectors.toSet());
        final String msg = "Are you sure you want to delete " + selectedItems.size() + " API keys?" +
                           "\n\nOnce deleted, anyone using these API Keys will no longer by able to " +
                           "authenticate with them and it will not be possible to re-create them.";
        ConfirmEvent.fire(this, msg, ok -> {
            if (ok) {
                restFactory
                        .create(API_KEY_RESOURCE)
                        .method(res ->
                                res.deleteBatch(ids))
                        .onSuccess(count -> {
                            onSuccess.run();
                        })
                        .onFailure(onFailure)
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    private void deleteSingle(final List<HashedApiKey> selectedItems,
                              final Runnable onSuccess,
                              final RestErrorHandler onFailure) {
        final HashedApiKey apiKey = selectedItems.get(0);
        final String msg = "Are you sure you want to delete API Key '"
                           + apiKey.getName()
                           + "' with prefix '"
                           + apiKey.getApiKeyPrefix()
                           + "'?" +
                           "\n\nOnce deleted, anyone using this API Key will no longer by able to authenticate with it "
                           + "and it will not be possible to re-create it.";
        ConfirmEvent.fire(this, msg, ok -> {
            if (ok) {
                restFactory
                        .create(API_KEY_RESOURCE)
                        .method(res -> res.delete(apiKey.getId()))
                        .onSuccess(unused -> {
                            onSuccess.run();
                        })
                        .onFailure(onFailure)
                        .taskMonitorFactory(this)
                        .exec();
            }
        });
    }

    @Override
    public void refresh() {
        apiKeysList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.KEY;
    }

    @Override
    public String getLabel() {
        return "Api Keys";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    /// Shows the API keys of one user.
    ///
    /// @param owner The user.
    public void showOwner(final UserRef owner) {
        apiKeysList.showUser(owner);
    }

    /// Shows the API keys of one user, as a user's tab does.
    ///
    /// @param owner The user.
    public void setOwner(final UserRef owner) {
        this.owner = owner;
        apiKeysList.setOwner(owner);
    }
}

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

package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.presenter.ApiKeysPresenter.ApiKeysView;
import stroom.security.client.presenter.EditApiKeyPresenter.Mode;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.HashedApiKey;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ApiKeysPresenter
        extends ContentTabPresenter<ApiKeysView>
        implements Refreshable, ApiKeyUiHandlers {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    public static final String TAB_TYPE = "ApiKeys";

    private final ApiKeysListPresenter listPresenter;
    private final Provider<EditApiKeyPresenter> editApiKeyPresenterProvider;
    private final RestFactory restFactory;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView deleteButton;
    private UserRef owner = null;

    @Inject
    public ApiKeysPresenter(final EventBus eventBus,
                            final ApiKeysView view,
                            final ApiKeysListPresenter listPresenter,
                            final RestFactory restFactory,
                            final UiConfigCache uiConfigCache,
                            final Provider<EditApiKeyPresenter> editApiKeyPresenterProvider) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.restFactory = restFactory;
        this.editApiKeyPresenterProvider = editApiKeyPresenterProvider;

//        view.setUiHandlers(this);
        view.setList(listPresenter.getView());

        addButton = listPresenter.addButton(SvgPresets.ADD.title("Add new API Key"));
        editButton = listPresenter.addButton(SvgPresets.EDIT.title("Edit API Key"));
        deleteButton = listPresenter.addButton(SvgPresets.DELETE.title("Delete API Key"));

//        uiConfigCache.get(uiConfig -> {
//            if (uiConfig != null) {
//                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
//                        "API Keys Quick Filter",
//                        FindApiKeyCriteria.FILTER_FIELD_DEFINITIONS,
//                        uiConfig.getHelpUrlQuickFilter()));
//            }
//        }, this);
    }

    private void setButtonStates() {
        final int selectedCount = NullSafe.size(getSelectedItems());
        editButton.setEnabled(selectedCount == 1);
        deleteButton.setEnabled(selectedCount > 0);
        if (selectedCount > 1) {
            deleteButton.setTitle("Delete " + selectedCount + " API Keys");
        } else {
            deleteButton.setTitle("Delete API Key");
        }
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(addButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                createNewKey();
            }
        }));
        registerHandler(editButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                editSelectedKey();
            }
        }));
        registerHandler(deleteButton.addClickHandler(e -> {
            if (MouseUtil.isPrimary(e)) {
                deleteSelectedKeys();
            }
        }));
        registerHandler(getSelectionModel().addSelectionHandler(e -> {
            onSelection();
            if (e.getSelectionType().isDoubleSelect()
                && NullSafe.hasOneItem(getSelectedItems())) {
                editSelectedKey();
            }
        }));
    }

    private void onSelection() {
        setButtonStates();
    }

    private List<HashedApiKey> getSelectedItems() {
        return listPresenter.getSelectionModel().getSelectedItems();
    }

    private MultiSelectionModelImpl<HashedApiKey> getSelectionModel() {
        return listPresenter.getSelectionModel();
    }

    private void createNewKey() {
        final boolean allowOwnerSelection = owner == null;
        editApiKeyPresenterProvider.get().showCreateDialog(
                Mode.PRE_CREATE,
                listPresenter::refresh,
                allowOwnerSelection);
    }

    private void editSelectedKey() {
        final HashedApiKey apiKey = getSelectionModel().getSelected();
        editApiKeyPresenterProvider.get().showEditDialog(apiKey, Mode.EDIT, listPresenter::refresh);
    }

    private void deleteSelectedKeys() {
        // This is the one selected using row selection, not the checkbox
//        final HashedApiKey selectedApiKey = getSelectionModel().getSelected();

        // The ones selected with the checkboxes
//        final Set<Integer> selectedSet = NullSafe.set(selection.getSet());
//        final boolean clearSelection = selectedApiKey != null && selectedSet.contains(selectedApiKey.getId());
//        final List<Integer> selectedItems = new ArrayList<>(selectedSet);
        final List<HashedApiKey> selectedItems = getSelectedItems();

        final Runnable onSuccess = () -> {
////            if (clearSelection) {
//                getSelectionModel().clear();
////            }
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
//                GWT.log("id: " + id);
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

    public void focus() {
        getView().focus();
    }

    @Override
    public void changeQuickFilterInput(final String userInput) {
        listPresenter.setQuickFilter(userInput);
    }

    @Override
    public void refresh() {
        listPresenter.refresh();
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

    public void showOwner(final UserRef owner) {
        listPresenter.showUser(owner);
    }

    public void setOwner(final UserRef owner) {
        this.owner = owner;
        listPresenter.setOwner(owner);
    }


    // --------------------------------------------------------------------------------


    //    public interface ApiKeysView extends View, HasUiHandlers<ApiKeyUiHandlers> {
    public interface ApiKeysView extends View {

//        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);

        void focus();

        void setList(View view);
    }
}

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

package stroom.credentials.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Provides the tabs for the Credentials Details dialog.
 * Based on stroom.dashboard.client.main.SettingsPresenter
 */
public class CredentialEditPresenter
        extends MyPresenterWidget<LinkTabsLayoutView>
        implements Focus {

    private final CredentialClient credentialClient;

    /**
     * The details presenter tab
     */
    final CredentialSettingsPresenter credentialSettingsPresenter;

    /**
     * The Permissions tab
     */
    final DocumentUserPermissionsPresenter permissionsPresenter;

    /**
     * Maps the tab header to the tab content
     */
    private final Map<TabData, Layer> tabViewMap = new HashMap<>();

    /**
     * First tab to show is the first tab added to this presenter
     */
    private TabData firstTab;

    /**
     * Currently selected tab
     */
    private TabData selectedTab;

    /**
     * ID for setInSlot()
     */
    public static final String CREDENTIALS_TABS = "credentials-tab";

    /**
     * Width of dialog - set for requirements of Permissions tab
     */
    private static final int DIALOG_WIDTH = 750;

    /**
     * Height of dialog
     */
    private static final int DIALOG_HEIGHT = 750;

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialEditPresenter(final EventBus eventBus,
                                   final LinkTabsLayoutView view,
                                   final CredentialSettingsPresenter credentialSettingsPresenter,
                                   final DocumentUserPermissionsPresenter permissionsPresenter,
                                   final CredentialClient credentialClient) {
        super(eventBus, view);
        this.credentialSettingsPresenter = credentialSettingsPresenter;
        this.permissionsPresenter = permissionsPresenter;
        this.credentialClient = credentialClient;
        getWidget().getElement().addClassName("default-min-sizes");
        final TabData detailsTab = addTab("Settings", credentialSettingsPresenter);
        changeSelectedTab(detailsTab);
        addTab("Permissions", permissionsPresenter);
    }

    /**
     * Called when adding or editing credentials.
     *
     * @param cwp           The credentials to add or edit. Can be null in which case nothing happens.
     * @param creationState Whether these are new or old credentials.
     */
    public void show(final DocRef docRef,
                     final CredentialWithPerms cwp,
                     final CreationState creationState,
                     final Consumer<Credential> consumer) {
        read(docRef, cwp);
        final String caption = CreationState.NEW_CREDENTIALS.equals(creationState)
                ?
                "New Credentials"
                : "Edit Credentials";
        // Configure the popup builder for this dialog
        ShowPopupEvent
                .builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption(caption)
                .modal(true)
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        credentialSettingsPresenter.onOk(valid -> {
                            if (valid) {
                                // Store or update the secret
                                final PutCredentialRequest request = credentialSettingsPresenter.write();
                                credentialClient.storeCredential(request,
                                        result -> {
                                            consumer.accept(result);
                                            e.hide();
                                        }, new DefaultErrorHandler(this, e::reset),
                                        this);
                            } else {
                                e.reset();
                            }
                        });
                    } else {
                        // TODO : DELETE ANY HALF INITIATED PERMS IF NEW

                        // Cancel pressed
                        e.hide();
                    }
                })
                .fire();
    }

    private void read(final DocRef docRef,
                      final CredentialWithPerms cwp) {
        credentialSettingsPresenter.read(docRef, cwp);
        permissionsPresenter.setDocRef(docRef);
    }

    /**
     * Called when the component gets focus.
     */
    @Override
    public void focus() {
        if (selectedTab != null) {
            final Layer layer = tabViewMap.get(selectedTab);
            if (layer instanceof Focus) {
                ((Focus) layer).focus();
            }
        } else {
            getView().getTabBar().focus();
        }
    }

    /**
     * Register handlers when added to the UI.
     */
    @Override
    public void onBind() {

        // Handler for the tab bar clicks to select different tabs.
        registerHandler(getView().getTabBar().addSelectionHandler(event -> {
            final TabData tab = event.getSelectedItem();
            if (tab != null && tab != selectedTab) {
                changeSelectedTab(tab);
            }
        }));
        registerHandler(getView().getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    /**
     * Adds a tab to the system. The first tab added will be the first selected.
     *
     * @param text  The text for the tab header.
     * @param layer The layer to show when the header is selected.
     * @return The thing to use to select the tab.
     */
    public TabData addTab(final String text, final Layer layer) {
        final TabData tab = new TabDataImpl(text, false);
        tabViewMap.put(tab, layer);
        getView().getTabBar().addTab(tab);

        if (firstTab == null) {
            firstTab = tab;
        }

        return tab;
    }

    /**
     * Call to change the selected tab.
     *
     * @param tab The tab to select. Can be null but not sure what will happen in that case.
     */
    private void changeSelectedTab(final TabData tab) {
        if (selectedTab != tab) {
            selectedTab = tab;
            if (selectedTab != null) {
                final Layer layer = tabViewMap.get(selectedTab);
                if (layer != null) {
                    getView().getTabBar().selectTab(tab);
                    getView().getLayerContainer().show(layer);
                }
            }
        }
    }

    /**
     * Indicates whether the credentials are new ones to be created or old ones to be stored
     */
    public enum CreationState {
        NEW_CREDENTIALS,
        OLD_CREDENTIALS
    }
}

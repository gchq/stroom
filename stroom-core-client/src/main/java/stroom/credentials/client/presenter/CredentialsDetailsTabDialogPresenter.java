package stroom.credentials.client.presenter;

import stroom.credentials.client.presenter.CredentialsSettingsPresenter.CredentialsSettingsView;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsWithPerms;
import stroom.docref.DocRef;
import stroom.security.client.presenter.DocumentUserPermissionsPresenter;
import stroom.widget.popup.client.event.ShowPopupEvent.Builder;
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

/**
 * Provides the tabs for the Credentials Details dialog.
 * Based on stroom.dashboard.client.main.SettingsPresenter
 */
public class CredentialsDetailsTabDialogPresenter
        extends MyPresenterWidget<LinkTabsLayoutView>
        implements Focus {

    /** The details presenter tab */
    final CredentialsSettingsPresenter detailsPresenter;

    /** The Permissions tab */
    final DocumentUserPermissionsPresenter permissionsPresenter;

    /** Maps the tab header to the tab content */
    private final Map<TabData, Layer> tabViewMap = new HashMap<>();

    /** First tab to show is the first tab added to this presenter */
    private TabData firstTab;

    /** Currently selected tab */
    private TabData selectedTab;

    /** ID for setInSlot() */
    public static final String CREDENTIALS_TABS = "credentials-tab";

    /** Width of dialog - set for requirements of Permissions tab */
    private static final int DIALOG_WIDTH = 750;

    /** Height of dialog */
    private static final int DIALOG_HEIGHT = 750;

    /**
     * Injected constructor.
     */
    @Inject
    public CredentialsDetailsTabDialogPresenter(final EventBus eventBus,
                                                final LinkTabsLayoutView view,
                                                final CredentialsSettingsPresenter detailsPresenter,
                                                final DocumentUserPermissionsPresenter permissionsPresenter) {
        super(eventBus, view);
        this.detailsPresenter = detailsPresenter;
        this.permissionsPresenter = permissionsPresenter;
        getWidget().getElement().addClassName("default-min-sizes");
        final TabData detailsTab = addTab("Settings", detailsPresenter);
        changeSelectedTab(detailsTab);
        addTab("Permissions", permissionsPresenter);
    }

    /**
     * Call with the builder to set up this dialog before calling .fire().
     *
     * @param cwp     provides information to display to the user.
     * @param secret  The secrets to show.
     * @param caption The title to show on the dialog box.
     * @param builder The builder to show this popup.
     */
    public void setupDialog(final CredentialsWithPerms cwp,
                            final CredentialsSecret secret,
                            final String caption,
                            final Builder builder) {

        // Populate the UI
        this.getCredentialsSettingsView().setCredentials(cwp, secret);

        // Create a fake DocRef for the credentials
        final DocRef docRef = new DocRef(Credentials.TYPE,
                cwp.getCredentials().getUuid(),
                cwp.getCredentials().getName());
        permissionsPresenter.setDocRef(docRef);

        // Configure the popup builder for this dialog
        builder.popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(DIALOG_WIDTH, DIALOG_HEIGHT))
                .caption(caption)
                .modal(true);
    }

    /**
     * Determines if the data entered is valid.
     */
    public boolean isValid() {
        return getCredentialsSettingsView().isValid();
    }

    /**
     * Returns the message to display in the AlertEvent if something
     * isn't valid.
     */
    public String getValidationMessage() {
        return getCredentialsSettingsView().getValidationMessage();
    }

    /**
     * @return The credentials view for the details presenter.
     */
    public CredentialsSettingsView getCredentialsSettingsView() {
        return detailsPresenter.getView();
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
     * @param text The text for the tab header.
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

}

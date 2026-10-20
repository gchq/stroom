/*
 * Copyright 2025 Crown Copyright
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

package stroom.contentstore.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackStatus;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.contentstore.shared.ContentStoreResponse.Status;
import stroom.credentials.client.presenter.CredentialsManagerDialogPresenter;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.util.client.NewTabLink;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import javax.inject.Inject;

/**
 * Details panel of the content store UI.
 */
public class ContentStoreContentPackDetailsPresenter
        extends FlowPanel
        implements HasHandlers {

    /**
     * Used when we need an empty string
     */
    private static final String EMPTY = "";

    // The text of the links, which say where they go rather than being the bare URL (WCAG 2.4.4).
    // NewTabLink adds that they open in a new tab, for screen readers
    private static final String LICENCE_LINK_TEXT = "View licence";
    private static final String GIT_LINK_TEXT = "View Git repository";

    /**
     * Converts markdown to HTML
     */
    private final MarkdownConverter markdownConverter;

    /**
     * Connection to the server
     */
    private final RestFactory restFactory;

    /**
     * Credentials dialog
     */
    private final CredentialsManagerDialogPresenter credentialsDialog;

    /**
     * Tells the client what App Permissions the user has
     */
    private final ClientSecurityContext securityContext;

    /**
     * Displays the icon of the content pack
     */
    private final HTML lblIcon = new HTML();

    /**
     * Displays the name of the content pack
     */
    private final Label lblName = new Label();

    /**
     * Displays whether the content pack is installed
     */
    private final Label lblIsInstalled = new Label();

    /**
     * Displays the basic licence info
     */
    private final Label lblLicense = new Label();

    /**
     * Link to full licence details
     */
    private final Anchor lnkLicense = new Anchor();

    /**
     * The URL of the full licence details, shown after its link
     */
    private final InlineLabel lblLicenseUrl = new InlineLabel();

    /**
     * Displays where the content pack will be installed in Stroom
     */
    private final Label lblStroomPath = new Label();

    /**
     * Link to the Git repository
     */
    private final Anchor lnkGitUrl = new Anchor();

    /**
     * Git URL, shown after its link
     */
    private final InlineLabel lblGitUrl = new InlineLabel();

    /**
     * Git branch
     */
    private final Label lblGitBranch = new Label();

    /**
     * Git path
     */
    private final Label lblGitPath = new Label();

    /**
     * Git commit
     */
    private final Label lblGitCommit = new Label();

    /**
     * Description of content pack
     */
    private final HTML lblDetails = new HTML();

    /**
     * Button to create the GitRepo
     */
    private final Button btnCreateGitRepo = new Button();

    /**
     * Button to upgrade the GitRepo to the latest content
     */
    private final Button btnUpgradeGitRepo = new Button();

    /**
     * Points to top level of this page. Needed for Alert dialogs.
     */
    private ContentStorePresenter contentStorePresenter = null;

    /**
     * Current content pack selected. Might be null
     */
    private ContentStoreContentPackWithDynamicState contentPackWithState = null;

    @SuppressWarnings("unused")
    @Inject
    public ContentStoreContentPackDetailsPresenter(final MarkdownConverter markdownConverter,
                                                   final RestFactory restFactory,
                                                   final CredentialsManagerDialogPresenter credentialsDialog,
                                                   final ClientSecurityContext securityContext) {

        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;
        this.credentialsDialog = credentialsDialog;
        this.securityContext = securityContext;

        addStyleName("contentstore-details");

        // Icon
        lblIcon.addStyleName("contentstore-details-icon");
        add(lblIcon);

        // Everything else is in a vertical stack
        final FlowPanel pnlVertical = new FlowPanel();
        pnlVertical.addStyleName("contentstore-details-vertical");
        add(pnlVertical);

        // Title - name of content pack
        lblName.addStyleName("contentstore-details-heading");

        // Details
        lblDetails.addStyleName("contentstore-details-markdown form-control-border form-control-background");
        lblDetails.setWordWrap(true);

        // Install button
        btnCreateGitRepo.setText("Install");
        btnCreateGitRepo.addClickHandler(event -> btnCreateGitRepoClick());

        // Upgrade Pack button
        btnUpgradeGitRepo.setText("Upgrade");
        btnUpgradeGitRepo.addClickHandler(event -> btnUpgradeGitRepoClick());

        // Add buttons centrally into a row
        final FlowPanel pnlButtons = new FlowPanel();
        pnlButtons.addStyleName("contentstore-buttons");
        pnlButtons.add(btnCreateGitRepo);
        pnlButtons.add(btnUpgradeGitRepo);

        // Main layout
        final FlowPanel detailsList = new FlowPanel();
        detailsList.addStyleName("contentstore-details-list");

        // Whether installed
        addDetail(detailsList, "Installed status:", lblIsInstalled);

        // Licence
        addDetail(detailsList, "Licence:", lblLicense);
        addDetail(detailsList, "Licence details:", linkWithUrl(lnkLicense, lblLicenseUrl));

        // Installed location
        addDetail(detailsList, "Installed location:", lblStroomPath);

        // Git details
        addDetail(detailsList, "Git URL:", linkWithUrl(lnkGitUrl, lblGitUrl));
        addDetail(detailsList, "Git branch:", lblGitBranch);
        addDetail(detailsList, "Git path:", lblGitPath);
        addDetail(detailsList, "Git commit:", lblGitCommit);

        // Add the panels into the structure
        pnlVertical.add(lblName);
        pnlVertical.add(lblDetails);
        pnlVertical.add(detailsList);
        pnlVertical.add(pnlButtons);

        // Ensure initial state is correct
        setContentPack(null);
    }

    // Adds a detail's name, and the widget showing its value beside it
    private static void addDetail(final FlowPanel detailsList, final String name, final Widget value) {
        final Label nameLabel = new Label(name);
        nameLabel.addStyleName("contentstore-details-name");
        detailsList.add(nameLabel);
        detailsList.add(value);
    }

    // A link followed by the URL it goes to, so the URL can still be seen (and copied)
    private static Widget linkWithUrl(final Anchor link, final InlineLabel url) {
        url.addStyleName("contentstore-details-url");
        final FlowPanel panel = new FlowPanel();
        panel.add(link);
        panel.add(url);
        return panel;
    }

    // Shows a link that opens the URL in a new tab, followed by the URL, or nothing (not an empty
    // link) when there is no URL
    private static void setLink(final Anchor link,
                                final InlineLabel urlLabel,
                                final String url,
                                final String text) {
        if (NullSafe.isBlankString(url)) {
            link.setVisible(false);
            urlLabel.setText(EMPTY);
        } else {
            NewTabLink.setLink(link, url, text);
            link.setVisible(true);
            urlLabel.setText(url);
        }
    }

    /**
     * Gives this component a reference to the top level of this page.
     * Must be called before UI is used.
     *
     * @param contentStorePresenter The top level of this page. Must not be null.
     */
    void setContentStorePresenter(final ContentStorePresenter contentStorePresenter) {
        this.contentStorePresenter = contentStorePresenter;
    }

    /**
     * Called when page is refreshed.
     */
    public void refresh() {
        this.setContentPack(null);
    }

    /**
     * Displays all the details of the given content pack.
     *
     * @param cpws The content pack and its state to display. Can be null
     *             in which case the display will be blank.
     */
    public void setContentPack(final ContentStoreContentPackWithDynamicState cpws) {
        // Store for click handler
        this.contentPackWithState = cpws;

        if (cpws == null) {
            this.lblIcon.setHTML(EMPTY);
            this.lblName.setText(EMPTY);
            this.lblIsInstalled.setText(EMPTY);
            this.lblLicense.setText(EMPTY);
            setLink(lnkLicense, lblLicenseUrl, null, LICENCE_LINK_TEXT);
            this.lblDetails.setText(EMPTY);
            this.lblStroomPath.setText(EMPTY);
            setLink(lnkGitUrl, lblGitUrl, null, GIT_LINK_TEXT);
            this.lblGitBranch.setText(EMPTY);
            this.lblGitPath.setText(EMPTY);
            this.lblGitCommit.setText(EMPTY);
            this.lblDetails.setHTML(EMPTY);
        } else {
            this.lblIcon.setHTML(ImageTagUtil.getImageTag(120, 120, cpws.getContentPack().getId()));
            this.lblName.setText(cpws.getContentPack().getUiName());
            this.lblIsInstalled.setText(cpws.getInstallationStatus().toString());
            this.lblLicense.setText(cpws.getContentPack().getLicenseName());
            setLink(lnkLicense, lblLicenseUrl, cpws.getContentPack().getLicenseUrl(), LICENCE_LINK_TEXT);
            this.lblStroomPath.setText(resolveInstalledLocation(cpws.getContentPack()));
            setLink(lnkGitUrl, lblGitUrl, cpws.getContentPack().getGitUrl(), GIT_LINK_TEXT);
            this.lblGitBranch.setText(cpws.getContentPack().getGitBranch());
            this.lblGitPath.setText(cpws.getContentPack().getGitPath());
            this.lblGitCommit.setText(cpws.getContentPack().getGitCommit());

            // Details get converted to markdown
            final SafeHtml safeDetails = markdownConverter.convertMarkdownToHtml(
                    cpws.getContentPack().getDetails());
            this.lblDetails.setHTML(safeDetails);
        }

        // Update state
        setState();
    }

    /**
     * Utility to generate the installed location field.
     *
     * @param contentPack The content pack with the info. Must not be null.
     * @return The string to display to the user.
     */
    private String resolveInstalledLocation(final ContentStoreContentPack contentPack) {
        final String stroomPath = contentPack.getStroomPath();
        final String gitRepoName = contentPack.getGitRepoName();
        final StringBuilder buf = new StringBuilder(stroomPath);
        if (!stroomPath.endsWith("/")) {
            buf.append('/');
        }
        buf.append(gitRepoName);
        return buf.toString();
    }

    /**
     * Sets the state of the UI components. Called when something
     * relevant changes.
     */
    public void setState() {
        if (contentPackWithState != null) {
            final ContentStoreContentPackStatus status = contentPackWithState.getInstallationStatus();
            if (status.equals(ContentStoreContentPackStatus.NOT_INSTALLED)) {
                btnCreateGitRepo.setEnabled(true);
                btnUpgradeGitRepo.setEnabled(false);
            } else if (status.equals(ContentStoreContentPackStatus.PACK_UPGRADABLE)
                       || status.equals(ContentStoreContentPackStatus.CONTENT_UPGRADABLE)) {
                btnCreateGitRepo.setEnabled(false);
                btnUpgradeGitRepo.setEnabled(true);
            } else {
                btnCreateGitRepo.setEnabled(false);
                btnUpgradeGitRepo.setEnabled(false);
            }

        } else {
            btnCreateGitRepo.setEnabled(false);
            btnUpgradeGitRepo.setEnabled(false);
        }
    }

    /**
     * Event handler called when the button 'Create Git Repo' is clicked.
     */
    private void btnCreateGitRepoClick() {
        if (contentPackWithState != null) {

            // Defensive copy of reference as everything is async
            final ContentStoreContentPackWithDynamicState cpws = contentPackWithState;

            // Ask for credentials if (contentPack.getGitNeedsAuth())
            if (cpws.getContentPack().getGitNeedsAuth()) {
                if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
                    final ShowPopupEvent.Builder builder = ShowPopupEvent.builder(credentialsDialog);
                    credentialsDialog.setupDialog(
                            builder,
                            cpws.getContentPack().getContentStoreMetadata().getAuthContact(),
                            null);
                    builder.onHideRequest(e -> {
                        if (e.isOk()) {
                            final String credentialsName = credentialsDialog.getCredentialName();

                            if (credentialsName != null) {
                                // Create the GitRepo with the given credentials
                                e.hide();
                                requestGitRepoCreation(cpws, credentialsName);
                            } else {
                                // Something is wrong
                                AlertEvent.fireWarn(credentialsDialog,
                                        "No credentials were selected; "
                                        + "this content pack cannot be downloaded",
                                        e::reset);
                            }
                        } else {
                            // Cancel pressed
                            e.hide();
                        }
                    })
                            .fire();
                } else {
                    // We need credentials but don't have permission to access them
                    AlertEvent.fireError(credentialsDialog,
                            "This content pack requires credentials, "
                            + "but you don't have permission to access credentials",
                            null);
                }

            } else {
                // No authentication needed
                requestGitRepoCreation(cpws, null);
            }
        }
    }

    /**
     * Performs the REST request to the server to create a GitRepo.
     *
     * @param cpws          The content pack with state. Must not be null.
     * @param credentialName The credentials name for authentication, if required.
     */
    private void requestGitRepoCreation(final ContentStoreContentPackWithDynamicState cpws,
                                        final String credentialName) {

        final ContentStoreCreateGitRepoRequest request =
                new ContentStoreCreateGitRepoRequest(cpws.getContentPack(), credentialName);

        restFactory
                .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(result -> {
                    if (result.getStatus().equals(Status.OK)) {
                        AlertEvent.fireInfo(contentStorePresenter,
                                "Creation success",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                        // Mark the content pack as Installed & update the UI
                        cpws.setInstallationStatus(ContentStoreContentPackStatus.INSTALLED);
                        contentStorePresenter.updateState();
                    } else {
                        AlertEvent.fireError(contentStorePresenter,
                                "Create failed",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                    }
                })
                .onFailure(restError -> AlertEvent.fireError(contentStorePresenter,
                        "Create failed",
                        restError.getMessage(),
                        () -> RefreshExplorerTreeEvent.fire(contentStorePresenter)))
                .taskMonitorFactory(btnCreateGitRepo)
                .exec();

    }

    /**
     * Called when the 'upgrade' button is pressed. This could
     * be a pack upgrade or a content upgrade.
     */
    private void btnUpgradeGitRepoClick() {
        //Window.alert("Upgrade button pressed");

        // Only do anything if something is selected
        if (contentPackWithState != null) {
            // Defensive copy of reference
            doContentPackUpgrade(contentPackWithState);
        }
    }

    /**
     * Called when the Upgrade button is clicked and the content
     * can be upgraded.
     *
     * @param cpws The current content pack and state. Must not be null.
     */
    private void doContentPackUpgrade(final ContentStoreContentPackWithDynamicState cpws) {
        restFactory
                .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                .method(res -> res.upgradeContentPack(cpws.getContentPack()))
                .onSuccess(result -> {
                    if (result.getStatus().equals(Status.OK)) {
                        AlertEvent.fireInfo(contentStorePresenter,
                                "Content pack upgrade success",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                        // Mark the content pack as Installed & update the UI
                        cpws.setInstallationStatus(ContentStoreContentPackStatus.INSTALLED);
                        contentStorePresenter.updateState();
                    } else {
                        AlertEvent.fireError(contentStorePresenter,
                                "Content pack upgrade failed",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                    }
                })
                .onFailure(restError -> AlertEvent.fireError(contentStorePresenter,
                        "Content pack upgrade failed",
                        restError.getMessage(),
                        () -> RefreshExplorerTreeEvent.fire(contentStorePresenter)))
                .taskMonitorFactory(btnUpgradeGitRepo)
                .exec();
    }

}

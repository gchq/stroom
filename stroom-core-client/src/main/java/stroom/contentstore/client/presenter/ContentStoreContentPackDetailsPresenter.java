package stroom.contentstore.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.data.table.client.Refreshable;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.widget.button.client.Button;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasAutoHorizontalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.VerticalPanel;

import javax.inject.Inject;

public class ContentStoreContentPackDetailsPresenter
    extends SimplePanel
    implements Refreshable, HasHandlers {

    /** Points to top level of this page. Needed for Alert dialogs. */
    private ContentStorePresenter contentStorePresenter = null;

    /** Converts markdown to HTML */
    private final MarkdownConverter markdownConverter;

    /** Connection to the server */
    private final RestFactory restFactory;

    /** Credentials dialog */
    private final ContentStoreCredentialsDialogPresenter credentialsDialog;

    /** Displays the icon of the content pack */
    private final HTML lblIcon = new HTML();

    /** Displays the name of the content pack */
    private final Label lblName = new Label();

    /** Displays whether the content pack is installed */
    private final Label lblIsInstalled = new Label();

    /** Displays the basic license info */
    private final Label lblLicense = new Label();

    /** Link to full license details */
    private final Anchor lnkLicense = new Anchor();

    /** Displays where the content pack will be installed in Stroom */
    private final Label lblStroomPath = new Label();

    /** Git URL */
    private final Anchor lnkGitUrl = new Anchor();

    /** Git branch */
    private final Label lblGitBranch = new Label();

    /** Git path */
    private final Label lblGitPath = new Label();

    /** Git commit */
    private final Label lblGitCommit = new Label();

    /** Description of content pack */
    private final HTML lblDetails = new HTML();

    /** Button to create the GitRepo */
    private final Button btnCreateGitRepo = new Button();

    /** Current content pack selected. Might be null */
    private ContentStoreContentPack contentPack = null;

    /** Used when we need an empty string */
    private final static String EMPTY = "";

    /** Target window of the licence URL */
    private final static String LICENCE_URL_TARGET = "stroom-content-pack-licence";

    /** Title (hover-over) for the licence URL link */
    private final static String LICENCE_URL_TITLE = "Link to licence (opens in new window)";

    /** Target window of the Git URL */
    private final static String GIT_URL_TARGET = "stroom-content-pack-git";

    /** Title (hover-over) for the GIT URL link */
    private final static String GIT_URL_TITLE = "Link to Git repository (opens in new window)";

    /**
     * Injected constructor.
     */
    @SuppressWarnings("unused")
    @Inject
    public ContentStoreContentPackDetailsPresenter(final MarkdownConverter markdownConverter,
                                                   final RestFactory restFactory,
                                                   final ContentStoreCredentialsDialogPresenter credentialsDialog) {

        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;
        this.credentialsDialog = credentialsDialog;

        final HorizontalPanel pnlHorizontal = new HorizontalPanel();
        pnlHorizontal.addStyleName("contentstore-details");

        // Icon
        lblIcon.addStyleName("contentstore-details-icon");
        pnlHorizontal.add(lblIcon);

        // Everything else is in a vertical stack
        final VerticalPanel pnlVertical = new VerticalPanel();
        pnlVertical.addStyleName("contentstore-details-vertical");
        pnlHorizontal.add(pnlVertical);

        // Main layout
        final FlexTable detailsTable = new FlexTable();
        detailsTable.addStyleName("contentstore-details-table");
        FlexCellFormatter detailsFormatter = detailsTable.getFlexCellFormatter();

        // Title - name of content pack
        lblName.addStyleName("contentstore-details-heading");
        detailsTable.setWidget(0, 0, lblName);
        detailsFormatter.setColSpan(0, 0, 2);
        detailsFormatter.setHorizontalAlignment(0, 0, HasAutoHorizontalAlignment.ALIGN_CENTER);

        // Details
        lblDetails.setWordWrap(true);
        detailsFormatter.setColSpan(1, 0, 2);
        detailsTable.setWidget(1, 0, lblDetails);

        // Install button
        btnCreateGitRepo.setText("Install");
        btnCreateGitRepo.addClickHandler(event -> btnCreateGitRepoClick());
        detailsFormatter.setColSpan(2, 0, 2);
        detailsFormatter.setHorizontalAlignment(2, 0, HasAutoHorizontalAlignment.ALIGN_CENTER);
        detailsTable.setWidget(2, 0, btnCreateGitRepo);

        // Whether installed
        detailsTable.setHTML(3, 0, "Installed status:");
        detailsTable.setWidget(3, 1, lblIsInstalled);

        // License
        detailsTable.setHTML(4, 0, "License:");
        detailsTable.setWidget(4, 1, lblLicense);

        lnkLicense.setTarget(LICENCE_URL_TARGET);
        lnkLicense.setTitle(LICENCE_URL_TITLE);
        detailsTable.setHTML(5, 0, "License details:");
        detailsTable.setWidget(5, 1, lnkLicense);

        // Installed location
        detailsTable.setHTML(6, 0, "Installed location:");
        detailsTable.setWidget(6, 1, lblStroomPath);

        // Git details
        lnkGitUrl.setTarget(GIT_URL_TARGET);
        lnkGitUrl.setTitle(GIT_URL_TITLE);

        detailsTable.setHTML(7, 0, "Git URL:");
        detailsTable.setWidget(7, 1, lnkGitUrl);
        detailsTable.setHTML(8, 0, "Git branch:");
        detailsTable.setWidget(8, 1, lblGitBranch);
        detailsTable.setHTML(9, 0, "Git path:");
        detailsTable.setWidget(9, 1, lblGitPath);
        detailsTable.setHTML(10, 0, "Git commit:");
        detailsTable.setWidget(10, 1, lblGitCommit);

        // Add the panels into the structure
        pnlVertical.add(detailsTable);

        // Add everything to the presenter's panel
        this.add(pnlHorizontal);

        // Ensure initial state is correct
        this.setState();
    }

    /**
     * Gives this component a reference to the top level of this page.
     * Must be called before UI is used.
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
     * @param cp The content pack to display. Can be null
     *           in which case the display will be blank.
     */
    public void setContentPack(final ContentStoreContentPack cp) {
        // Store for click handler
        this.contentPack = cp;

        if (cp == null) {
            this.lblIcon.setHTML(EMPTY);
            this.lblName.setText(EMPTY);
            this.lblIsInstalled.setText(EMPTY);
            this.lblLicense.setText(EMPTY);
            this.lnkLicense.setHref(EMPTY);
            this.lnkLicense.setText(EMPTY);
            this.lblDetails.setText(EMPTY);
            this.lblStroomPath.setText(EMPTY);
            this.lnkGitUrl.setHref(EMPTY);
            this.lnkGitUrl.setText(EMPTY);
            this.lblGitBranch.setText(EMPTY);
            this.lblGitPath.setText(EMPTY);
            this.lblGitCommit.setText(EMPTY);
            this.lblDetails.setHTML(EMPTY);
        } else {
            this.lblIcon.setHTML(cp.getIconSvg());
            this.lblName.setText(cp.getUiName());
            this.lblIsInstalled.setText(cp.isInstalled() ? "Installed" : "-");
            this.lblLicense.setText(cp.getLicenseName());
            this.lnkLicense.setHref(cp.getLicenseUrl());
            this.lnkLicense.setText(cp.getLicenseUrl());
            this.lblStroomPath.setText(resolveInstalledLocation(cp));
            this.lnkGitUrl.setHref(cp.getGitUrl());
            this.lnkGitUrl.setText(cp.getGitUrl());
            this.lblGitBranch.setText(cp.getGitBranch());
            this.lblGitPath.setText(cp.getGitPath());
            this.lblGitCommit.setText(cp.getGitCommit());

            // Details get converted to markdown
            SafeHtml safeDetails = markdownConverter.convertMarkdownToHtml(cp.getDetails());
            this.lblDetails.setHTML(safeDetails);
        }

        // Update state
        this.setState();
    }

    /**
     * Utility to generate the installed location field.
     * @param cp The content pack with the info. Must not be null.
     * @return The string to display to the user.
     */
    private String resolveInstalledLocation(final ContentStoreContentPack cp) {
        String stroomPath = cp.getStroomPath();
        String gitRepoName = cp.getGitRepoName();
        StringBuilder buf = new StringBuilder(stroomPath);
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
    private void setState() {
        if (contentPack != null) {
            if (contentPack.isInstalled()) {
                btnCreateGitRepo.setEnabled(false);
            } else {
                btnCreateGitRepo.setEnabled(true);
            }

        } else {
            btnCreateGitRepo.setEnabled(false);
        }
    }

    /**
     * Event handler called when the button 'Create Git Repo' is clicked.
     */
    private void btnCreateGitRepoClick() {
        if (contentPack != null) {

            // Ask for credentials if (contentPack.getGitNeedsAuth())
            if (contentPack.getGitNeedsAuth()) {
                ShowPopupEvent.Builder builder = ShowPopupEvent.builder(credentialsDialog);
                credentialsDialog.setupBuilder(
                        contentPack,
                        builder);
                builder.onHideRequest(e -> {
                            if (e.isOk()) {
                                if (credentialsDialog.isValid()) {
                                    // Create the GitRepo with the given credentials
                                    requestGitRepoCreation(contentPack,
                                            credentialsDialog.getView().getUsername(),
                                            credentialsDialog.getView().getPassword());
                                } else {
                                    // Something is wrong
                                    AlertEvent.fireWarn(credentialsDialog,
                                            credentialsDialog.getValidationMessage(),
                                            e::reset);
                                }
                            } else {
                                // Cancel pressed
                                e.hide();
                            }
                        })
                        .fire();

            } else {
                // No authentication needed
                requestGitRepoCreation(contentPack, null, null);
            }
        }
    }

    /**
     * Performs the REST request to the server to create a GitRepo.
     * @param cp The content pack. Must not be null.
     * @param username The username for authentication, if required.
     * @param password The password for authentication, if required.
     */
    private void requestGitRepoCreation(final ContentStoreContentPack cp,
                                        final String username,
                                        final String password) {

        ContentStoreCreateGitRepoRequest request =
                new ContentStoreCreateGitRepoRequest(cp,
                        username,
                        password);

        restFactory
                .create(ContentStorePresenter.CONTENT_STORE_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(contentStorePresenter,
                                "Creation success",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                    } else {
                        AlertEvent.fireError(contentStorePresenter,
                                "Create failed",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                    }
                })
                .onFailure(restError -> {
                    AlertEvent.fireError(contentStorePresenter,
                            "Create failed",
                            restError.getMessage(),
                            () -> RefreshExplorerTreeEvent.fire(contentStorePresenter));
                })
                .taskMonitorFactory(btnCreateGitRepo)
                .exec();


    }

}

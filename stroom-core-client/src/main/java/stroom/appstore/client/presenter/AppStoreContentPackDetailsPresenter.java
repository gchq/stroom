package stroom.appstore.client.presenter;

import stroom.appstore.shared.AppStoreContentPack;
import stroom.data.table.client.Refreshable;
import stroom.entity.client.presenter.MarkdownConverter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasAutoHorizontalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;

import javax.inject.Inject;

public class AppStoreContentPackDetailsPresenter
    extends SimplePanel
    implements Refreshable {

    /** Converts markdown to HTML */
    private final MarkdownConverter markdownConverter;

    /** Displays the icon of the content pack */
    private final HTML lblIcon = new HTML();

    /** Displays the name of the content pack */
    private final Label lblName = new Label();

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
    public AppStoreContentPackDetailsPresenter(MarkdownConverter markdownConverter) {
        this.markdownConverter = markdownConverter;

        DockPanel root = new DockPanel();
        root.setSpacing(100);

        // Icon
        lblIcon.setHeight("50px");
        lblIcon.setWidth("50px");
        lblIcon.setStyleName("svg-image", true);
        root.add(lblIcon, DockPanel.WEST);

        // Main layout
        FlexTable detailsTable = new FlexTable();
        detailsTable.setCellSpacing(6);
        FlexCellFormatter detailsFormatter = detailsTable.getFlexCellFormatter();

        // Title - name of content pack
        detailsTable.setWidget(0, 0, lblName);
        detailsFormatter.setColSpan(0, 0, 2);
        detailsFormatter.setHorizontalAlignment(0, 0, HasAutoHorizontalAlignment.ALIGN_CENTER);

        // License
        detailsTable.setHTML(1, 0, "License:");
        detailsTable.setWidget(1, 1, lblLicense);

        lnkLicense.setTarget(LICENCE_URL_TARGET);
        lnkLicense.setTitle(LICENCE_URL_TITLE);
        detailsTable.setHTML(2, 0, "License details:");
        detailsTable.setWidget(2, 1, lnkLicense);

        // Installed location
        detailsTable.setHTML(3, 0, "Installed location:");
        detailsTable.setWidget(3, 1, lblStroomPath);

        // Git details
        lnkGitUrl.setTarget(GIT_URL_TARGET);
        lnkGitUrl.setTitle(GIT_URL_TITLE);
        detailsTable.setHTML(4, 0, "Git URL:");
        detailsTable.setWidget(4, 1, lnkGitUrl);
        detailsTable.setHTML(5, 0, "Git branch:");
        detailsTable.setWidget(5, 1, lblGitBranch);
        detailsTable.setHTML(6, 0, "Git path:");
        detailsTable.setWidget(6, 1, lblGitPath);
        detailsTable.setHTML(7, 0, "Git commit:");
        detailsTable.setWidget(7, 1, lblGitCommit);

        // Details
        lblDetails.setWordWrap(true);
        detailsTable.setHTML(8, 0, "Details:");
        detailsTable.setWidget(8, 1, lblDetails);

        // Add the panels into the structure
        DecoratorPanel decoratorPanel = new DecoratorPanel();
        decoratorPanel.setWidget(detailsTable);
        root.add(decoratorPanel, DockPanel.CENTER);
        this.add(root);
    }

    public void refresh() {
        // Ignore
    }

    /**
     * Displays all the details of the given content pack.
     * @param cp The content pack to display. Can be null
     *           in which case the display will be blank.
     */
    public void setContentPack(AppStoreContentPack cp) {

        if (cp == null) {
            this.lblIcon.setHTML(EMPTY);
            this.lblName.setText(EMPTY);
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
            this.lblLicense.setText(cp.getLicenseName());
            this.lnkLicense.setHref(cp.getLicenseUrl());
            this.lnkLicense.setText(cp.getLicenseUrl());
            this.lblStroomPath.setText(cp.getStroomPath());
            this.lnkGitUrl.setHref(cp.getGitUrl());
            this.lnkGitUrl.setText(cp.getGitUrl());
            this.lblGitBranch.setText(cp.getGitBranch());
            this.lblGitPath.setText(cp.getGitPath());
            this.lblGitCommit.setText(cp.getGitCommit());

            // Details get converted to markdown
            SafeHtml safeDetails = markdownConverter.convertMarkdownToHtml(cp.getDetails());
            this.lblDetails.setHTML(safeDetails);
        }
    }
}

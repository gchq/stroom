/*
 * Copyright 2026 Crown Copyright
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

package stroom.contentstore.client.component;

import stroom.contentstore.client.ImageTagUtil;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Html;
import stroom.widget.text.client.InlineLabel;
import stroom.widget.text.client.Label;
import stroom.widget.text.client.Link;
import stroom.widget.util.client.NewTabLink;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The details of the content pack selected in the content store: its icon, name and
/// description, where it comes from and goes, and Install and Upgrade.
public class ContentStoreContentPackDetails extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    // Used when we need an empty string
    private static final String EMPTY = "";

    // The text of the links, which say where they go rather than being the bare URL (WCAG 2.4.4).
    // NewTabLink adds that they open in a new tab, for screen readers
    private static final String LICENCE_LINK_TEXT = "View licence";
    private static final String GIT_LINK_TEXT = "View Git repository";

    @UiField
    Html icon;
    @UiField
    Label name;
    @UiField
    Html details;
    @UiField
    Label installedStatus;
    @UiField
    Label licence;
    @UiField
    Link licenceLink;
    @UiField
    InlineLabel licenceUrl;
    @UiField
    Label installedLocation;
    @UiField
    Link gitLink;
    @UiField
    InlineLabel gitUrl;
    @UiField
    Label gitBranch;
    @UiField
    Label gitPath;
    @UiField
    Label gitCommit;
    @UiField
    Button install;
    @UiField
    Button upgrade;

    /// Makes empty details, with Install and Upgrade disabled.
    public ContentStoreContentPackDetails() {
        initWidget(BINDER.createAndBindUi(this));
        setContentPack(null, null);
        setActions(false, false);
    }

    /// Shows a content pack's details.
    ///
    /// @param cpws        The content pack and its state, or null to show nothing.
    /// @param description The pack's description, as HTML, or null for none.
    public void setContentPack(final ContentStoreContentPackWithDynamicState cpws, final SafeHtml description) {
        if (cpws == null) {
            icon.setHTML(EMPTY);
            name.setText(EMPTY);
            installedStatus.setText(EMPTY);
            licence.setText(EMPTY);
            setLink(licenceLink, licenceUrl, null, LICENCE_LINK_TEXT);
            installedLocation.setText(EMPTY);
            setLink(gitLink, gitUrl, null, GIT_LINK_TEXT);
            gitBranch.setText(EMPTY);
            gitPath.setText(EMPTY);
            gitCommit.setText(EMPTY);
            details.setHTML(EMPTY);
        } else {
            final ContentStoreContentPack contentPack = cpws.getContentPack();
            icon.setHTML(ImageTagUtil.getImageTag(120, 120, contentPack.getId()));
            name.setText(contentPack.getUiName());
            installedStatus.setText(cpws.getInstallationStatus().toString());
            licence.setText(contentPack.getLicenseName());
            setLink(licenceLink, licenceUrl, contentPack.getLicenseUrl(), LICENCE_LINK_TEXT);
            installedLocation.setText(resolveInstalledLocation(contentPack));
            setLink(gitLink, gitUrl, contentPack.getGitUrl(), GIT_LINK_TEXT);
            gitBranch.setText(contentPack.getGitBranch());
            gitPath.setText(contentPack.getGitPath());
            gitCommit.setText(contentPack.getGitCommit());
            if (description != null) {
                details.setHTML(description);
            } else {
                details.setHTML(EMPTY);
            }
        }
    }

    /// @param installEnabled Whether Install can be pressed.
    /// @param upgradeEnabled Whether Upgrade can be pressed.
    public void setActions(final boolean installEnabled, final boolean upgradeEnabled) {
        install.setEnabled(installEnabled);
        upgrade.setEnabled(upgradeEnabled);
    }

    /// @param onInstall What to run when the user presses Install.
    public void setOnInstall(final Runnable onInstall) {
        install.setOnClick(onInstall);
    }

    /// @param onUpgrade What to run when the user presses Upgrade.
    public void setOnUpgrade(final Runnable onUpgrade) {
        upgrade.setOnClick(onUpgrade);
    }

    /// @return Install, whose spinner shows while the pack is installed.
    public TaskMonitorFactory getInstallTaskMonitor() {
        return install;
    }

    /// @return Upgrade, whose spinner shows while the pack is upgraded.
    public TaskMonitorFactory getUpgradeTaskMonitor() {
        return upgrade;
    }

    // Shows a link that opens the URL in a new tab, followed by the URL, or nothing (not an empty
    // link) when there is no URL
    private static void setLink(final Link link,
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

    // Where the pack is installed in Stroom: its path, then its Git repository's name
    private static String resolveInstalledLocation(final ContentStoreContentPack contentPack) {
        final String stroomPath = contentPack.getStroomPath();
        final String gitRepoName = contentPack.getGitRepoName();
        final StringBuilder buf = new StringBuilder(stroomPath);
        if (!stroomPath.endsWith("/")) {
            buf.append('/');
        }
        buf.append(gitRepoName);
        return buf.toString();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ContentStoreContentPackDetails> {

    }
}

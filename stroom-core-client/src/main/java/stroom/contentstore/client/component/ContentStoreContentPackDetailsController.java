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

package stroom.contentstore.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.contentstore.shared.ContentStoreContentPackStatus;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.contentstore.shared.ContentStoreResource;
import stroom.contentstore.shared.ContentStoreResponse.Status;
import stroom.credentials.client.component.CredentialsPickerController;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.MarkdownConverter;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The details of the content pack selected in the content store
/// ([ContentStoreContentPackDetails]): installs it (asking for credentials if its repository needs
/// them) or upgrades it, and tells its owner when it has.
public class ContentStoreContentPackDetailsController extends Controller {

    private static final ContentStoreResource CONTENT_STORE_RESOURCE = GWT.create(ContentStoreResource.class);

    private ContentStoreContentPackDetails view;
    private final MarkdownConverter markdownConverter;
    private final RestFactory restFactory;
    private final Provider<CredentialsPickerController> credentialsDialogProvider;
    private final ClientSecurityContext securityContext;

    // The content pack selected, or null
    private ContentStoreContentPackWithDynamicState contentPackWithState = null;
    private Runnable onInstalled = () -> {
    };

    /// Made by GIN; [#attach(ContentStoreContentPackDetails)] gives it its details.
    ///
    /// @param eventBus                  The application's event bus.
    /// @param markdownConverter         Renders the pack's description.
    /// @param restFactory               Installs and upgrades the pack.
    /// @param credentialsDialogProvider Makes the dialog that asks for the repository's credentials.
    /// @param securityContext           The user's permissions.
    @Inject
    public ContentStoreContentPackDetailsController(
            final EventBus eventBus,
            final MarkdownConverter markdownConverter,
            final RestFactory restFactory,
            final Provider<CredentialsPickerController> credentialsDialogProvider,
            final ClientSecurityContext securityContext) {
        super(eventBus);
        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;
        this.credentialsDialogProvider = credentialsDialogProvider;
        this.securityContext = securityContext;
    }

    /// Attaches this to a pack's details, e.g. ones a template declares. The details are then this
    /// controller's widget.
    ///
    /// @param view The details.
    /// @return This, for its owner to keep.
    public ContentStoreContentPackDetailsController attach(final ContentStoreContentPackDetails view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        view.setOnInstall(this::install);
        view.setOnUpgrade(this::upgrade);
        return this;
    }

    /// @param onInstalled What to run when a pack has been installed or upgraded, e.g. to show its
    ///                    new state in the list.
    public void setOnInstalled(final Runnable onInstalled) {
        this.onInstalled = onInstalled;
    }

    /// Shows nothing, e.g. when the content store is refreshed.
    public void refresh() {
        setContentPack(null);
    }

    /// Shows a content pack's details.
    ///
    /// @param cpws The content pack and its state, or null to show nothing.
    public void setContentPack(final ContentStoreContentPackWithDynamicState cpws) {
        this.contentPackWithState = cpws;
        final SafeHtml description = cpws != null
                ? markdownConverter.convertMarkdownToHtml(cpws.getContentPack().getDetails())
                : null;
        view.setContentPack(cpws, description);
        updateState();
    }

    /// Makes Install and Upgrade available as the pack's state allows, e.g. after it changes.
    public void updateState() {
        if (contentPackWithState != null) {
            final ContentStoreContentPackStatus status = contentPackWithState.getInstallationStatus();
            if (status.equals(ContentStoreContentPackStatus.NOT_INSTALLED)) {
                view.setActions(true, false);
            } else if (status.equals(ContentStoreContentPackStatus.PACK_UPGRADABLE)
                       || status.equals(ContentStoreContentPackStatus.CONTENT_UPGRADABLE)) {
                view.setActions(false, true);
            } else {
                view.setActions(false, false);
            }
        } else {
            view.setActions(false, false);
        }
    }

    // Install: asks for credentials first if the pack's repository needs them
    private void install() {
        if (contentPackWithState != null) {
            // Defensive copy of reference as everything is async
            final ContentStoreContentPackWithDynamicState cpws = contentPackWithState;

            if (cpws.getContentPack().getGitNeedsAuth()) {
                if (securityContext.hasAppPermission(AppPermission.CREDENTIALS)) {
                    // Made for this use, and owned until it is hidden; the GitRepo is made with the
                    // credentials chosen
                    credentialsDialogProvider.get().show(this,
                            cpws.getContentPack().getContentStoreMetadata().getAuthContact(),
                            null,
                            "No credentials were selected; this content pack cannot be downloaded",
                            credentialsName -> requestGitRepoCreation(cpws, credentialsName));
                } else {
                    // We need credentials but don't have permission to access them
                    AlertEvent.fireError(this,
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

    // Creates the pack's GitRepo on the server
    private void requestGitRepoCreation(final ContentStoreContentPackWithDynamicState cpws,
                                        final String credentialName) {
        final ContentStoreCreateGitRepoRequest request =
                new ContentStoreCreateGitRepoRequest(cpws.getContentPack(), credentialName);

        restFactory
                .create(CONTENT_STORE_RESOURCE)
                .method(res -> res.create(request))
                .onSuccess(result -> {
                    if (result.getStatus().equals(Status.OK)) {
                        AlertEvent.fireInfo(this,
                                "Creation success",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(this));
                        // Mark the content pack as Installed & update the UI
                        cpws.setInstallationStatus(ContentStoreContentPackStatus.INSTALLED);
                        onInstalled.run();
                    } else {
                        AlertEvent.fireError(this,
                                "Create failed",
                                result.getMessage(),
                                () -> RefreshExplorerTreeEvent.fire(this));
                    }
                })
                .onFailure(restError -> AlertEvent.fireError(this,
                        "Create failed",
                        restError.getMessage(),
                        () -> RefreshExplorerTreeEvent.fire(this)))
                .owner(this)
                .taskMonitorFactory(view.getInstallTaskMonitor())
                .exec();
    }

    // Upgrade: the pack, or its content
    private void upgrade() {
        // Only do anything if something is selected
        if (contentPackWithState != null) {
            // Defensive copy of reference
            final ContentStoreContentPackWithDynamicState cpws = contentPackWithState;
            restFactory
                    .create(CONTENT_STORE_RESOURCE)
                    .method(res -> res.upgradeContentPack(cpws.getContentPack()))
                    .onSuccess(result -> {
                        if (result.getStatus().equals(Status.OK)) {
                            AlertEvent.fireInfo(this,
                                    "Content pack upgrade success",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(this));
                            // Mark the content pack as Installed & update the UI
                            cpws.setInstallationStatus(ContentStoreContentPackStatus.INSTALLED);
                            onInstalled.run();
                        } else {
                            AlertEvent.fireError(this,
                                    "Content pack upgrade failed",
                                    result.getMessage(),
                                    () -> RefreshExplorerTreeEvent.fire(this));
                        }
                    })
                    .onFailure(restError -> AlertEvent.fireError(this,
                            "Content pack upgrade failed",
                            restError.getMessage(),
                            () -> RefreshExplorerTreeEvent.fire(this)))
                    .owner(this)
                    .taskMonitorFactory(view.getUpgradeTaskMonitor())
                    .exec();
        }
    }
}

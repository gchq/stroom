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
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.AppPermission;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.HashAlgorithm;
import stroom.security.shared.HashedApiKey;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Clipboard;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// Creates or edits an API key ([EditApiKey]) in a dialog made for one use. Once a key is made the
/// dialog shows it, so it can be copied: it is never shown again.
public class EditApiKeyController extends Controller {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    private final EditApiKey view;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final UiConfigCache uiConfigCache;
    private final UserRefSelectionController owner;
    private final Timer hideCopied;

    private HashedApiKey apiKey;
    private Runnable onChangeHandler;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param restFactory                   Makes, saves and deletes keys.
    /// @param securityContext               Says whether the user may choose the owner.
    /// @param uiConfigCache                 Gives the longest a key may live and the default hash.
    /// @param ownerProvider                 Makes the owner picker's controller.
    /// @param dateTimeBoxControllerProvider Makes the expiry box's controller.
    @Inject
    public EditApiKeyController(final EventBus eventBus,
                                final EditApiKey view,
                                final RestFactory restFactory,
                                final ClientSecurityContext securityContext,
                                final UiConfigCache uiConfigCache,
                                final Provider<UserRefSelectionController> ownerProvider,
                                final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);
        owner = make(ownerProvider);
        owner.attach(view.getOwnerBox());
        make(dateTimeBoxControllerProvider).attach(view.getExpiresOnBox());

        // When the key is copied say so, for a while
        hideCopied = new Timer() {
            @Override
            public void run() {
                view.setCopiedShown(false);
            }
        };
        onDispose(hideCopied::cancel);
        view.setOnCopy(() -> {
            Clipboard.writeText(view.getApiKey());
            view.setCopiedShown(true);
            hideCopied.schedule(3_000);
        });

        reset();
    }

    /// Shows the dialog that makes a key, owned by `opener` until it is closed.
    ///
    /// @param opener              What shows it.
    /// @param mode                The mode to start in, [Mode#PRE_CREATE].
    /// @param onChangeHandler     Called once a key is made, kept or deleted.
    /// @param allowOwnerSelection Whether the owner may be changed from the user.
    public void showCreateDialog(final HasControllerNode opener,
                                 final Mode mode,
                                 final Runnable onChangeHandler,
                                 final boolean allowOwnerSelection) {
        this.onChangeHandler = onChangeHandler;
        setMode(mode);

        final String caption;
        if (Mode.PRE_CREATE.equals(mode)) {
            caption = "Create new API key";
            // Default to current user
            owner.setSelected(securityContext.getUserRef());
            owner.setEnabled(allowOwnerSelection);
        } else if (Mode.POST_CREATE.equals(mode)) {
            caption = "View created API key";
        } else {
            caption = "Edit API key";
        }

        uiConfigCache.get(guard(uiConfig -> {
            if (uiConfig == null) {
                // Not shown, so nothing else would dispose it
                dispose();
                return;
            }
            if (Mode.PRE_CREATE.equals(mode)) {
                view.setHashAlgorithm(Objects.requireNonNullElse(
                        uiConfig.getDefaultApiKeyHashAlgorithm(),
                        HashAlgorithm.DEFAULT));
            }
            showDialog(opener, caption);
        }), taskMonitor());
    }

    /// Shows the dialog that edits a key, owned by `opener` until it is closed.
    ///
    /// @param opener          What shows it.
    /// @param apiKey          The key.
    /// @param mode            The mode, [Mode#EDIT].
    /// @param onChangeHandler Called once the key is saved.
    public void showEditDialog(final HasControllerNode opener,
                               final HashedApiKey apiKey,
                               final Mode mode,
                               final Runnable onChangeHandler) {
        this.onChangeHandler = onChangeHandler;
        this.apiKey = apiKey;
        setMode(mode);

        owner.setSelected(apiKey.getOwner());
        view.setName(apiKey.getName());
        view.setPrefix(apiKey.getApiKeyPrefix());
        view.setComments(apiKey.getComments());
        view.setExpiresOn(apiKey.getExpireTimeMs());
        view.setEnabled(apiKey.getEnabled());
        view.setHashAlgorithm(apiKey.getHashAlgorithm());
        showDialog(opener, "Edit API key");
    }

    private void showDialog(final HasControllerNode opener, final String caption) {
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX(600))
                .caption(caption)
                .onShow(e -> view.focus())
                .onHideRequest(this::onHideRequest)
                .fire();
    }

    private void setMode(final Mode mode) {
        owner.setEnabled(Mode.PRE_CREATE.equals(mode)
                         && securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION));
        view.setMode(mode);
        reset();
    }

    private void reset() {
        uiConfigCache.get(guard(uiConfig ->
                view.reset(System.currentTimeMillis() + uiConfig.getMaxApiKeyExpiryAgeMs())), taskMonitor());
    }

    // Package-private for the tests
    void onHideRequest(final HidePopupRequestEvent e) {
        final Mode mode = view.getMode();
        if (e.isOk() || Mode.POST_CREATE.equals(mode)) {
            uiConfigCache.get(guard(uiConfig -> {
                if (uiConfig != null) {
                    if (Mode.PRE_CREATE.equals(mode)) {
                        handlePreCreateModeHide(e, uiConfig);
                    } else if (Mode.POST_CREATE.equals(mode)) {
                        handlePostCreateModeHide(e);
                    } else if (Mode.EDIT.equals(mode)) {
                        handleEditModeHide(e);
                    }
                } else {
                    e.hide();
                }
            }), taskMonitor());
        } else {
            e.hide();
        }
    }

    private void handleEditModeHide(final HidePopupRequestEvent e) {
        if (!e.isOk()) {
            e.hide();
        } else if (NullSafe.isBlankString(view.getName())) {
            AlertEvent.fireError(this, "A name must be provided for the API key.", e::reset);
        } else {
            final HashedApiKey updatedApiKey = apiKey
                    .copy()
                    .name(view.getName())
                    .comments(view.getComments())
                    .enabled(view.isEnabled())
                    .build();
            restFactory
                    .create(API_KEY_RESOURCE)
                    .method(res -> res.update(apiKey.getId(), updatedApiKey))
                    .onSuccess(saved -> {
                        apiKey = saved;
                        onChangeHandler.run();
                        e.hide();
                    })
                    .onFailure(throwable ->
                            AlertEvent.fireError(this, "Error updating API key: " + throwable.getMessage(), e::reset))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private void handlePostCreateModeHide(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            ConfirmEvent.fire(this,
                    "You will never be able to view the API Key after you close "
                    + "this dialog. Stroom does not store the API Key. You must copy it elsewhere first. "
                    + "Are you sure you want to close this dialog?",
                    ok -> {
                        if (ok) {
                            onChangeHandler.run();
                            e.hide();
                        } else {
                            e.reset();
                        }
                    });
        } else {
            ConfirmEvent.fire(this,
                    "Cancelling will delete the API Key that you have just created, are you sure?",
                    ok -> {
                        if (ok) {
                            // cancel clicked so delete the created key
                            restFactory
                                    .create(API_KEY_RESOURCE)
                                    .method(res -> res.delete(apiKey.getId()))
                                    .onSuccess(didDelete -> {
                                        onChangeHandler.run();
                                        e.hide();
                                    })
                                    .onFailure(throwable ->
                                            AlertEvent.fireError(this, "Error deleting API key: "
                                                                       + throwable.getMessage(), e::reset))
                                    .owner(this)
                                    .taskMonitorFactory(taskMonitor())
                                    .exec();
                        } else {
                            e.reset();
                        }
                    });
        }
    }

    private void handlePreCreateModeHide(final HidePopupRequestEvent event,
                                         final ExtendedUiConfig uiConfig) {
        final long now = System.currentTimeMillis();
        final Long expireTimeEpochMs = view.getExpiresOnMs();
        final long maxExpiryEpochMs = now + uiConfig.getMaxApiKeyExpiryAgeMs();
        final UserRef selectedOwner = owner.getSelected();
        if (expireTimeEpochMs == null) {
            // Blank, or text that isn't a date and time
            AlertEvent.fireError(this, "A valid expiry date must be provided for the API key.", event::reset);
        } else if (expireTimeEpochMs < now) {
            AlertEvent.fireError(this, "API Key expiry date cannot be in the past.", event::reset);
        } else if (expireTimeEpochMs > maxExpiryEpochMs) {
            AlertEvent.fireError(this, "API Key expiry date cannot be after "
                                       + ClientDateUtil.toISOString(maxExpiryEpochMs), event::reset);
        } else if (NullSafe.isBlankString(view.getName())) {
            AlertEvent.fireError(this, "A name must be provided for the API key.", event::reset);
        } else if (selectedOwner == null) {
            AlertEvent.fireError(this, "An owner must be provided for the API key.", event::reset);
        } else {
            final CreateHashedApiKeyRequest request = new CreateHashedApiKeyRequest(
                    selectedOwner,
                    expireTimeEpochMs,
                    view.getName(),
                    view.getComments(),
                    view.isEnabled(),
                    view.getHashAlgorithm());
            restFactory
                    .create(API_KEY_RESOURCE)
                    .method(res -> res.create(request))
                    .onSuccess(response -> {
                        apiKey = response.getHashedApiKey();
                        // API Key created so change the mode and update the fields on the dialog
                        // so the user can see the actual API key
                        setMode(Mode.POST_CREATE);
                        owner.setSelected(apiKey.getOwner());
                        view.setExpiresOn(apiKey.getExpireTimeMs());
                        view.setName(apiKey.getName());
                        view.setComments(apiKey.getComments());
                        view.setEnabled(apiKey.getEnabled());
                        view.setApiKey(response.getApiKey());
                        view.setPrefix(apiKey.getApiKeyPrefix());
                        view.setHashAlgorithm(apiKey.getHashAlgorithm());

                        event.reset();
                        onChangeHandler.run();
                    })
                    .onFailure(throwable ->
                            AlertEvent.fireError(this, "Error creating API key: "
                                                       + throwable.getMessage(), event::reset))
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    private TaskMonitorFactory taskMonitor() {
        final TaskMonitorFactory provided = getProvidedTaskMonitor();
        return provided != null ? provided : new DefaultTaskMonitorFactory(this);
    }


    // --------------------------------------------------------------------------------


    /// What the dialog is doing with the key.
    public enum Mode {
        /// When the user is initially entering data to create the key.
        PRE_CREATE,
        /// Immediately after creation of the key, when the API key string is still in memory and
        /// can be shown.
        POST_CREATE,
        /// Editing of the record at a later date when the API key string is no longer available to
        /// show to the user.
        EDIT
    }
}

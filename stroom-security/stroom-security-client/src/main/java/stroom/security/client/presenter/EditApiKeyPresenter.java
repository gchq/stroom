package stroom.security.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.client.presenter.ErrorEvent;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.CreateApiKeyResponse;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserName;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class EditApiKeyPresenter
        extends MyPresenterWidget<EditApiKeyView>
        implements HidePopupRequestEvent.Handler, HasHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);
    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
//    private final ChooserPresenter<UserName> ownerChooserPresenter;
    private final UiConfigCache uiConfigCache;

    private ApiKey apiKey;
    private Runnable onChangeHandler = null;

    @Inject
    public EditApiKeyPresenter(final EventBus eventBus,
                               final EditApiKeyView view,
                               final RestFactory restFactory,
                               final ClientSecurityContext securityContext,
//                               final ChooserPresenter<UserName> ownerChooserPresenter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
//        this.ownerChooserPresenter = ownerChooserPresenter;
        this.uiConfigCache = uiConfigCache;
//        initOwnerChooserPresenter(ownerChooserPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();
//        registerHandler(ownerChooserPresenter.addDataSelectionHandler(e -> {
//            final UserName selected = ownerChooserPresenter.getSelected();
//            getView().setOwner(selected);
//        }));
        getView().setCanSelectOwner(securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION));

        final Rest<List<UserName>> rest = restFactory.create();
        rest
                .onSuccess(userNames ->
                        getView().setUserNames(userNames))
                .call(USER_RESOURCE)
                .getAssociates(null);
    }

//    private void initOwnerChooserPresenter(final ChooserPresenter<UserName> ownerChooserPresenter) {
//        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
//            ownerChooserPresenter.setDisplayValueFunction(userName -> {
//                if (userName != null) {
//                    if (!GwtNullSafe.isBlankString(userName.getFullName())) {
//                        return userName.getUserIdentityForAudit() + " (" + userName.getFullName() + ")";
//                    } else {
//                        return userName.getUserIdentityForAudit();
//                    }
//                } else {
//                    return null;
//                }
//            });
//
//            ownerChooserPresenter.setDataSupplier((filter, consumer) -> {
//                final Rest<List<UserName>> rest = restFactory.create();
//                rest
//                        .onSuccess(userNames -> consumer.accept(userNames.stream()
//                                .sorted(Comparator.comparing(UserName::getUserIdentityForAudit))
//                                .collect(Collectors.toList())))
//                        .call(USER_RESOURCE)
//                        .getAssociates(filter);
//            });
//        }
//    }

    public void showCreateDialog(final Mode mode,
                                 final Runnable onChangeHandler) {
        this.onChangeHandler = onChangeHandler;
        getView().setMode(mode);
        getView().clear();
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));

        final String caption;

        if (Mode.PRE_CREATE.equals(mode)) {
            caption = "Create new API key";
            // Default to current user
            getView().setOwner(securityContext.getUserName());
        } else if (Mode.POST_CREATE.equals(mode)) {
            caption = "View created API key";
        } else {
            caption = "Edit API key";
        }

        final PopupSize popupSize = PopupSize.resizableX(600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e ->
                        getView().focus())
                .onHideRequest(this)
                .fire();
    }

    public void showEditDialog(final ApiKey apiKey,
                               final Mode mode,
                               final Runnable onChangeHandler) {
        this.onChangeHandler = onChangeHandler;
        this.apiKey = apiKey;
        getView().setMode(mode);
        getView().clear();
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));
        getView().setOwner(apiKey.getOwner());
        getView().setName(apiKey.getName());
        getView().setPrefix(apiKey.getApiKeyPrefix());
        getView().setComments(apiKey.getComments());
        getView().setExpiresOn(apiKey.getExpireTimeMs());
        getView().setEnabled(apiKey.getEnabled());

        final PopupSize popupSize = PopupSize.resizableX(600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit API key")
                .onShow(e ->
                        getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent event) {
//        GWT.log("event: " + event);
        if (event.isOk()) {
            uiConfigCache.get().onSuccess(uiConfig -> {
                final Mode mode = getView().getMode();
//                GWT.log("mode: " + mode);
                if (Mode.PRE_CREATE.equals(mode)) {
                    handlePreCreateModeHide(event, uiConfig);
                } else if (Mode.POST_CREATE.equals(mode)) {
                    handlePostCreateModeHide(event);
                } else if (Mode.EDIT.equals(mode)) {
                    handleEditModeHide(event);
                }
            });
        } else {
            event.hide();
        }
    }

    private void handleEditModeHide(final HidePopupRequestEvent event) {
        if (event.isOk()) {
            if (GwtNullSafe.isBlankString(getView().getName())) {
                AlertEvent.fireError(this, "A name must be provided for the API key.", null);
            } else {
                final ApiKey updatedApiKey = ApiKey.builder(this.apiKey)
                        .withName(getView().getName())
                        .withComments(getView().getComments())
                        .withEnabled(getView().isEnabled())
                        .build();

//                GWT.log("ID: " + this.apiKey.getId());

                final Rest<ApiKey> rest = restFactory.create();
                rest
                        .onSuccess(apiKey -> {
                            this.apiKey = apiKey;
                            GwtNullSafe.run(onChangeHandler);
                            event.hide();
                        })
                        .onFailure(throwable -> {
                            ErrorEvent.fire(this, "Error updating API key: "
                                    + throwable.getMessage());
                        })
                        .call(API_KEY_RESOURCE)
                        .update(this.apiKey.getId(), updatedApiKey);
            }
        }
    }

    private void handlePostCreateModeHide(final HidePopupRequestEvent event) {
        ConfirmEvent.fire(this,
                "You will never be able to view the API Key after you close " +
                        "this dialog. Stroom does not store the API Key. You must copy it elsewhere first. " +
                        "Are you sure you want to close this dialog?",
                ok -> {
                    if (ok) {
                        event.hide();
                    }
                });
    }

    private void handlePreCreateModeHide(final HidePopupRequestEvent event,
                                         final ExtendedUiConfig uiConfig) {
        final long expireTimeEpochMs = getView().getExpiresOnMs();
        final long maxExpiryEpochMs = System.currentTimeMillis() + uiConfig.getMaxApiKeyExpiryAgeMs();
        final UserName owner = getView().getOwner();
        if (expireTimeEpochMs > maxExpiryEpochMs) {
            AlertEvent.fireError(this, "API Key expiry date cannot be after "
                    + ClientDateUtil.toISOString(maxExpiryEpochMs), null);
        } else if (GwtNullSafe.isBlankString(getView().getName())) {
            AlertEvent.fireError(this, "A name must be provided for the API key.", null);
        } else if (owner == null) {
            AlertEvent.fireError(this, "An owner must be provided for the API key.", null);
        } else {
            CreateApiKeyRequest request = new CreateApiKeyRequest(
                    owner,
                    expireTimeEpochMs,
                    getView().getName(),
                    getView().getComments(),
                    getView().isEnabled());
//            GWT.log("sending create req");
            final Rest<CreateApiKeyResponse> rest = restFactory.create();
            rest
                    .onSuccess(response -> {
                        apiKey = response.getHashedApiKey();
                        // API Key created so change the mode and update the fields on the dialog
                        // so the user can see the actual API key
                        getView().setMode(Mode.POST_CREATE);
                        getView().clear();
                        getView().setOwner(apiKey.getOwner());
                        getView().setExpiresOn(apiKey.getExpireTimeMs());
                        getView().setName(apiKey.getName());
                        getView().setComments(apiKey.getComments());
                        getView().setEnabled(apiKey.getEnabled());
                        getView().setApiKey(response.getApiKey());
                        getView().setPrefix(apiKey.getApiKeyPrefix());

                        GwtNullSafe.run(onChangeHandler);
                    })
                    .onFailure(throwable -> {
                        AlertEvent.fireError(this, "Error creating API key: "
                                + throwable.getMessage(), null);
                    })
                    .call(API_KEY_RESOURCE)
                    .create(request);
        }
    }


    // --------------------------------------------------------------------------------


    public enum Mode {
        /**
         * When the user is initially entering data to create the key.
         */
        PRE_CREATE,
        /**
         * Immediately after creation of the key, when the API key is still in memory and
         * can be shown.
         */
        POST_CREATE,
        /**
         * Editing of the record at a later date when the API key is no longer
         * available to show to the user.
         */
        EDIT
    }


    // --------------------------------------------------------------------------------


    public interface EditApiKeyView extends View, HasUiHandlers<HideRequestUiHandlers> {

//        void setCanSelectOwner(final boolean canSelectOwner);

        void setCanSelectOwner(boolean canSelectOwner);

        void setMode(final Mode mode);

        Mode getMode();

        void setUserNames(final List<UserName> userNames);

        void setOwner(final UserName owner);

        UserName getOwner();

        void setName(final String name);

        String getName();

        void setApiKey(final String apiKey);

        void setPrefix(final String prefix);

        void setComments(final String comments);

        String getComments();

        void setExpiresOn(final Long expiresOn);

        Long getExpiresOnMs();

        void setEnabled(final boolean isEnabled);

        boolean isEnabled();

        void focus();

        void clear();
    }
}

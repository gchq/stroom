package stroom.security.client.presenter;

import stroom.annotation.client.ChooserPresenter;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.EditApiKeyPresenter.EditApiKeyView;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserResource;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserName;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DefaultHideRequestUiHandlers;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class EditApiKeyPresenter extends MyPresenterWidget<EditApiKeyView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private final ChooserPresenter<UserName> ownerChooserPresenter;

    @Inject
    public EditApiKeyPresenter(final EventBus eventBus,
                               final EditApiKeyView view,
                               final RestFactory restFactory,
                               final ClientSecurityContext securityContext,
                               final ChooserPresenter<UserName> ownerChooserPresenter) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        this.ownerChooserPresenter = ownerChooserPresenter;
        initOwnerChooserPresenter(ownerChooserPresenter);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(ownerChooserPresenter.addDataSelectionHandler(e -> {
            final UserName selected = ownerChooserPresenter.getSelected();
            getView().setOwner(selected);
        }));
    }

    private void initOwnerChooserPresenter(final ChooserPresenter<UserName> ownerChooserPresenter) {
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            ownerChooserPresenter.setDisplayValueFunction(userName -> {
                if (userName != null) {
                    if (!GwtNullSafe.isBlankString(userName.getFullName())) {
                        return userName.getUserIdentityForAudit() + " ("  + userName.getFullName() + ")";
                    } else {
                        return userName.getUserIdentityForAudit();
                    }
                } else {
                    return null;
                }
            });

            ownerChooserPresenter.setDataSupplier((filter, consumer) -> {
                final Rest<List<UserName>> rest = restFactory.create();
                rest
                        .onSuccess(userNames -> consumer.accept(userNames.stream()
                                .sorted(Comparator.comparing(UserName::getUserIdentityForAudit))
                                .collect(Collectors.toList())))
                        .call(USER_RESOURCE)
                        .getAssociates(filter);
            });
        }
    }

    public void show(final Mode mode,
                     final HidePopupRequestEvent.Handler handler) {
        getView().setMode(mode);
        getView().setUiHandlers(new DefaultHideRequestUiHandlers(this));
        getView().clear();

        final String caption;

        if (Mode.PRE_CREATE.equals(mode)) {
            caption = "Create new API key";
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
                .onHideRequest(handler)
                .fire();
    }

    public enum Mode {
        /**
         * When the user is initially entering data to create the key
         */
        PRE_CREATE,
        /**
         * Immediately after creation of the key, when the API key is still in memory and
         * can be shown
         */
        POST_CREATE,
        /**
         * Editing of the record at a later date when the API key is no longer
         * available to show to the user
         */
        EDIT
    }


    // --------------------------------------------------------------------------------


    public interface EditApiKeyView extends View, HasUiHandlers<HideRequestUiHandlers> {

        void setMode(final Mode mode);

        void setOwner(final UserName owner);

        UserName getOwner(final UserName owner);

        void setName(final String name);

        String getName();

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

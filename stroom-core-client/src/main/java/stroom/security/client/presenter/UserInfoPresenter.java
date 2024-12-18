package stroom.security.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.UserInfoPresenter.UserInfoView;
import stroom.security.shared.AppPermission;
import stroom.security.shared.UserResource;
import stroom.util.shared.UserRef;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

/**
 * The Info sub-tab of UserTabPresenter
 */
public class UserInfoPresenter
        extends MyPresenterWidget<UserInfoView> {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private UserRef userRef;

    @Inject
    public UserInfoPresenter(final EventBus eventBus,
                             final UserInfoView view,
                             final RestFactory restFactory,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getIsEnabledTickBox().addValueChangeHandler(event -> {
            if (event.getValue() != null) {
                onEnabledStateChange(event.getValue());
            }
        }));
    }

    private void onEnabledStateChange(final boolean isEnabled) {
        if (userRef != null) {
            if (isEnabled != userRef.isEnabled()) {
                restFactory
                        .create(USER_RESOURCE)
                        .method(resource -> resource.fetch(userRef.getUuid()))
                        .onSuccess(user -> {
                            if (isEnabled != user.isEnabled()) {
                                user.setEnabled(isEnabled);
                                restFactory
                                        .create(USER_RESOURCE)
                                        .method(resource -> resource.update(user))
                                        .onSuccess(aVoid ->
                                                userRef = user.asRef())
                                        .taskMonitorFactory(this)
                                        .exec();
                            }
                        })
                        .taskMonitorFactory(this)
                        .exec();
            }
        }
    }

    public void setUserRef(final UserRef userRef) {
        this.userRef = userRef;
        getView().setUserRef(userRef);
        getView().setReadOnly(hasPermissionToEdit());
    }

    private boolean hasPermissionToEdit() {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION);
    }


    // --------------------------------------------------------------------------------


    public interface UserInfoView extends View {

        void setUserRef(final UserRef userRef);

        void setReadOnly(boolean isReadOnly);

        CustomCheckBox getIsEnabledTickBox();
    }
}

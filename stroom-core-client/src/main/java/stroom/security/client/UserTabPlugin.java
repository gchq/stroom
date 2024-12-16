package stroom.security.client;

import stroom.alert.client.event.AlertEvent;
import stroom.core.client.ContentManager;
import stroom.data.client.AbstractTabPresenterPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.client.presenter.UserTabPresenter;
import stroom.security.shared.AppPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.UserRef;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class UserTabPlugin extends AbstractTabPresenterPlugin<UserRef, UserTabPresenter> {

    public static final Preset USER_ICON = SvgPresets.USER;
    public static final Preset GROUP_ICON = SvgPresets.USER_GROUP;

    private final ClientSecurityContext securityContext;

    @Inject
    public UserTabPlugin(final EventBus eventBus,
                         final ContentManager contentManager,
                         final Provider<UserTabPresenter> userTabPresenterProvider,
                         final ClientSecurityContext securityContext) {
        super(eventBus, contentManager, userTabPresenterProvider);
        this.securityContext = securityContext;

        registerHandler(getEventBus().addHandler(OpenUserEvent.getType(), event -> {
            GWT.log("handling event");
            open(event.getUserRef(), true);
        }));


    }

    public void open(final UserRef userRef, final boolean forceOpen) {
        if (userRef != null) {
            if (hasPermissionToOpenUser(userRef)) {

                super.openTabPresenter(
                        forceOpen,
                        userRef,
                        userTabPresenter ->
                                userTabPresenter.setUserRef(userRef));
            } else {
                AlertEvent.fireError(this, "You do not have permission to open user '"
                                           + userRef.toDisplayString() + "'.", null);
            }
        }
    }

    private boolean hasPermissionToOpenUser(final UserRef userRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
               || securityContext.isCurrentUser(userRef);
    }

    @Override
    protected String getName() {
        return "User";
    }
}

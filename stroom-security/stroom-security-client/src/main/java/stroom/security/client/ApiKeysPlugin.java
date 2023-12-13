package stroom.security.client;

import stroom.core.client.MenuKeys;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.NodeToolsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.presenter.ApiKeysPresenter;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class ApiKeysPlugin extends NodeToolsPlugin {

    private final AsyncProvider<ApiKeysPresenter> apiKeysPresenterAsyncProvider;

    @Inject
    public ApiKeysPlugin(final EventBus eventBus,
                         final ClientSecurityContext securityContext,
                         final AsyncProvider<ApiKeysPresenter> apiKeysPresenterAsyncProvider) {
        super(eventBus, securityContext);
        this.apiKeysPresenterAsyncProvider = apiKeysPresenterAsyncProvider;
    }

    @Override
    protected void addChildItems(BeforeRevealMenubarEvent event) {
        if (getSecurityContext().hasAppPermission(PermissionNames.MANAGE_API_KEYS)) {
            MenuKeys.addSecurityMenu(event.getMenuItems());
            addMenuItem(event);
        }
    }

    private void addMenuItem(final BeforeRevealMenubarEvent event) {
        final IconMenuItem apiKeysMenuItem;
        final SvgImage icon = SvgImage.KEY;
        apiKeysMenuItem = new IconMenuItem.Builder()
                .priority(3)
                .icon(icon)
                .text("Manage API Keys")
                .command(this::show)
                .build();
        event.getMenuItems().addMenuItem(MenuKeys.SECURITY_MENU, apiKeysMenuItem);
    }

    private void show() {
        apiKeysPresenterAsyncProvider.get(new AsyncCallback<ApiKeysPresenter>() {
            @Override
            public void onSuccess(final ApiKeysPresenter presenter) {
                final PopupSize popupSize = PopupSize.resizable(1_100, 800);
                ShowPopupEvent.builder(presenter)
                        .popupType(PopupType.CLOSE_DIALOG)
                        .popupSize(popupSize)
                        .caption("Manage API Keys")
                        .onShow(e -> presenter.focus())
                        .fire();
            }

            @Override
            public void onFailure(final Throwable caught) {

            }
        });
    }
}

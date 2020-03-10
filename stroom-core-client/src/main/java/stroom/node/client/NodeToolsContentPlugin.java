package stroom.node.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.core.client.ContentManager;
import stroom.data.table.client.Refreshable;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.tab.client.presenter.TabData;

public abstract class NodeToolsContentPlugin<P extends MyPresenterWidget<?>>
    extends NodeToolsPlugin {

    private final ContentManager contentManager;
    private final Provider<P> presenterProvider;
    private P presenter;

    @Inject
    NodeToolsContentPlugin(final EventBus eventBus,
                           final ContentManager contentManager,
                           final Provider<P> presenterProvider,
                           final ClientSecurityContext securityContext) {
        super(eventBus, securityContext);
        this.contentManager = contentManager;
        this.presenterProvider = presenterProvider;
    }

    public void open() {
        if (presenter == null) {
            // If the presenter is null then we haven't got this tab open.
            // Create a new presenter.
            presenter = presenterProvider.get();
        }

        final ContentManager.CloseHandler closeHandler = callback -> {
            // Give the content manager the ok to close the tab.
            callback.closeTab(true);

            // After we close the tab set the presenter back to null so
            // that we can open it again.
            presenter = null;
        };

        // Tell the content manager to open the tab.
        final TabData tabData = (TabData) presenter;
        contentManager.open(closeHandler, tabData, presenter);

        // If the presenter is refreshable then refresh it.
        if (presenter instanceof Refreshable) {
            final Refreshable refreshable = (Refreshable) presenter;
            refreshable.refresh();
        }
    }
}

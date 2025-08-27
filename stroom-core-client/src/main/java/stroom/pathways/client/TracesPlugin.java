package stroom.pathways.client;

import stroom.content.client.ContentPlugin;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.docref.DocRef;
import stroom.pathways.client.presenter.ShowTracesEvent;
import stroom.pathways.client.presenter.TracesPresenter;
import stroom.pathways.shared.PathwaysDoc;
import stroom.security.client.api.event.CurrentUserChangedEvent;

import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Singleton;

@Singleton
public class TracesPlugin extends ContentPlugin<TracesPresenter> {

    @Inject
    public TracesPlugin(final EventBus eventBus,
                        final Provider<TracesPresenter> tracesPresenterProvider,
                        final ContentManager contentManager) {
        super(eventBus, contentManager, tracesPresenterProvider);


        registerHandler(getEventBus().addHandler(
                ShowTracesEvent.getType(), showTracesEvent -> {

                    final TracesPresenter tracesPresenter = tracesPresenterProvider.get();
                    tracesPresenter.setDataSourceRef(showTracesEvent.getDataSourceRef());
                    tracesPresenter.setPathway(showTracesEvent.getPathway());
                    tracesPresenter.setFilter(showTracesEvent.getFilter());
                    tracesPresenter.refresh();

                    final CloseContentEvent.Handler closeHandler = (event) -> {
                        event.getCallback().closeTab(true);
                    };

                    // Tell the content manager to open the tab.
                    contentManager.open(closeHandler, tracesPresenter, tracesPresenter);
                }));

        // TODO : TEMPORARY
        registerHandler(getEventBus().addHandler(CurrentUserChangedEvent.getType(), e -> {
            new Timer() {
                @Override
                public void run() {
                    final TracesPresenter tracesPresenter = tracesPresenterProvider.get();
                    tracesPresenter.setDataSourceRef(DocRef.builder().type(PathwaysDoc.TYPE).uuid(
                            "ba8df4b8-d03b-484c-bb65-273b35ca56ff").build());
//        tracesPresenter.setPathway();
//        tracesPresenter.setFilter();
                    tracesPresenter.refresh();

                    final CloseContentEvent.Handler closeHandler = (event) -> {
                        event.getCallback().closeTab(true);
                    };

                    // Tell the content manager to open the tab.
                    contentManager.open(closeHandler, tracesPresenter, tracesPresenter);
                }
            }.schedule(1000);
        }));


    }
}


package stroom.annotations.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class AnnotationsPlugin extends Plugin {

    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;

    private String annotationsURL;

    @Inject
    public AnnotationsPlugin(final EventBus eventBus,
                             final Provider<IFramePresenter> iFramePresenterProvider,
                             final ContentManager contentManager,
                             final ClientPropertyCache clientPropertyCache) {
        super(eventBus);
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.contentManager = contentManager;
        clientPropertyCache.get()
                .onSuccess(result -> annotationsURL = result.get(ClientProperties.URL_ANNOTATIONS_UI))
                .onFailure(caught -> AlertEvent.fireError(AnnotationsPlugin.this, caught.getMessage(), null));
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(BeforeRevealMenubarEvent.getType(), this));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU,
                new IconMenuItem(5, SvgPresets.EXPLORER, null, "Annotations", null, true, () -> {
                    final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                            .title("Annotations")
                            .href(annotationsURL)
                            .target(HyperlinkTarget.STROOM_TAB)
                            .build();
                    final IFramePresenter iFramePresenter = iFramePresenterProvider.get();
                    iFramePresenter.setHyperlink(hyperlink);
                    contentManager.open(callback ->
                                    ConfirmEvent.fire(AnnotationsPlugin.this,
                                            "Are you sure you want to close " + hyperlink.getTitle() + "?",
                                            callback::closeTab)
                            , iFramePresenter, iFramePresenter);
                }));
    }
}

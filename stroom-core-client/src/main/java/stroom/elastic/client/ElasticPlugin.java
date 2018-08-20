package stroom.elastic.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ElasticPlugin extends Plugin {
    private final Provider<IFrameContentPresenter> iFramePresenterProvider;
    private final ContentManager contentManager;
    private final UiConfigCache clientPropertyCache;

    @Inject
    public ElasticPlugin(final EventBus eventBus,
                         final Provider<IFrameContentPresenter> iFramePresenterProvider,
                         final ContentManager contentManager,
                         final UiConfigCache clientPropertyCache) {
        super(eventBus);
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.contentManager = contentManager;
        this.clientPropertyCache = clientPropertyCache;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(BeforeRevealMenubarEvent.getType(), this));
    }

    @Override
    public void onReveal(final BeforeRevealMenubarEvent event) {
        clientPropertyCache.get()
                .onSuccess(result -> {
                    final IconMenuItem annotationsMenuItem;
                    final String elasticUiUrl = result.getUrlConfig().getKibana();
                    if (elasticUiUrl != null && elasticUiUrl.trim().length() > 0) {
                        annotationsMenuItem = new IconMenuItem(6, SvgPresets.ELASTIC_SEARCH, null, "Elastic Search", null, true, () -> {
                            final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                                    .title("Elastic Search")
                                    .href(elasticUiUrl)
                                    .target(HyperlinkTarget.BROWSER_TAB)
                                    .build();
                            final IFrameContentPresenter presenter = iFramePresenterProvider.get();
                            presenter.setHyperlink(hyperlink);
                            presenter.setIcon(SvgPresets.ELASTIC_SEARCH);
                            contentManager.open(
                                    callback -> {
                                        callback.closeTab(true);
                                        presenter.close();
                                    },
                                    presenter, presenter);
                        });
                    } else {
                        annotationsMenuItem = new IconMenuItem(5, SvgPresets.ELASTIC_SEARCH, SvgPresets.ELASTIC_SEARCH, "Elastic Search UI is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, annotationsMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ElasticPlugin.this, caught.getMessage(), null));


    }
}

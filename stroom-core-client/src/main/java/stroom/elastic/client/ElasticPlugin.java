package stroom.elastic.client;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.Hyperlink.Builder;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.hyperlink.client.HyperlinkType;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class ElasticPlugin extends Plugin {
    private final UiConfigCache clientPropertyCache;

    @Inject
    public ElasticPlugin(final EventBus eventBus,
                         final UiConfigCache clientPropertyCache) {
        super(eventBus);
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
                            final Hyperlink hyperlink = new Builder()
                                    .text("Elastic Search")
                                    .href(elasticUiUrl)
                                    .type(HyperlinkType.BROWSER.name().toLowerCase())
                                    .icon(SvgPresets.ELASTIC_SEARCH)
                                    .build();
                            HyperlinkEvent.fire(this, hyperlink);
                        });
                    } else {
                        annotationsMenuItem = new IconMenuItem(5, SvgPresets.ELASTIC_SEARCH, SvgPresets.ELASTIC_SEARCH, "Elastic Search UI is not configured!", null, false, null);
                    }

                    event.getMenuItems().addMenuItem(MenuKeys.TOOLS_MENU, annotationsMenuItem);
                })
                .onFailure(caught -> AlertEvent.fireError(ElasticPlugin.this, caught.getMessage(), null));
    }
}

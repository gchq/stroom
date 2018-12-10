package stroom.dashboard.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HandlerContainerImpl;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkType;
import stroom.core.client.ContentManager;
import stroom.dashboard.client.event.HyperlinkEvent;
import stroom.dashboard.client.event.ShowDashboardEvent;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Map;

@Singleton
public class HyperlinkEventHandlerImpl extends HandlerContainerImpl implements HyperlinkEvent.Handler, HasHandlers {
    private final EventBus eventBus;
    private final Provider<IFrameContentPresenter> iFrameContentPresenterProvider;
    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;

    private Map<String, String> namedUrls;

    @Inject
    public HyperlinkEventHandlerImpl(final EventBus eventBus,
                                     final Provider<IFramePresenter> iFramePresenterProvider,
                                     final Provider<IFrameContentPresenter> iFrameContentPresenterProvider,
                                     final ContentManager contentManager,
                                     final ClientPropertyCache clientPropertyCache) {
        this.eventBus = eventBus;
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.iFrameContentPresenterProvider = iFrameContentPresenterProvider;
        this.contentManager = contentManager;

        clientPropertyCache.get()
                .onSuccess(result -> namedUrls = result.getLookupTable(ClientProperties.URL_LIST, ClientProperties.URL_BASE))
                .onFailure(caught -> AlertEvent.fireError(HyperlinkEventHandlerImpl.this, caught.getMessage(), null));

        registerHandler(eventBus.addHandler(HyperlinkEvent.getType(), this));
    }

    @Override
    public void onLink(final HyperlinkEvent event) {
        final Hyperlink hyperlink = event.getHyperlink();

        final String title = hyperlink.getTitle();

        String href = hyperlink.getHref();
        if (namedUrls != null) {
            for (final Map.Entry<String, String> namedUrlLookupEntry : namedUrls.entrySet()) {
                href = href.replaceAll("__" + namedUrlLookupEntry.getKey() + "__", namedUrlLookupEntry.getValue());
            }
        }

        HyperlinkType hyperlinkType = null;
        if (hyperlink.getType() != null) {
            try {
                hyperlinkType = HyperlinkType.valueOf(hyperlink.getType().toUpperCase());
            } catch (final RuntimeException e) {
                GWT.log("Could not parse open type value of " + hyperlink.getType());
            }
        }

        if (hyperlinkType != null) {
            switch (hyperlinkType) {
                case DASHBOARD: {
                    ShowDashboardEvent.fire(this, title, href);
                    break;
                }
                case TAB: {
                    final IFrameContentPresenter presenter = iFrameContentPresenterProvider.get();
                    presenter.setHyperlink(hyperlink);
                    contentManager.open(callback ->
                                    ConfirmEvent.fire(this,
                                            "Are you sure you want to close " + hyperlink.getTitle() + "?",
                                            res -> {
                                                if (res) {
                                                    presenter.close();
                                                }
                                                callback.closeTab(res);
                                            })
                            , presenter, presenter);
                    break;
                }
                case DIALOG: {
                    final PopupSize popupSize = new PopupSize(800, 600, true);
                    final IFramePresenter presenter = iFramePresenterProvider.get();
                    presenter.setHyperlink(hyperlink);
                    ShowPopupEvent.fire(this,
                            presenter,
                            PopupType.CLOSE_DIALOG,
                            null,
                            popupSize,
                            title,
                            null,
                            null);
                    break;
                }
                case BROWSER: {
                    Window.open(href, "_blank", "");
                }
                default:
                    Window.open(href, "_blank", "");
            }
        } else {
            Window.open(href, "_blank", "");
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}

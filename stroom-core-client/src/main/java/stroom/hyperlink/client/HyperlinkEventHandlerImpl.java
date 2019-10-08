package stroom.hyperlink.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainerImpl;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.ShowAnnotationEvent;
import stroom.core.client.ContentManager;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.streamstore.client.presenter.ShowDataEvent;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Highlight;
import stroom.pipeline.shared.SourceLocation;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
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

        String href = hyperlink.getHref();
        if (namedUrls != null) {
            for (final Map.Entry<String, String> namedUrlLookupEntry : namedUrls.entrySet()) {
                href = href.replaceAll("__" + namedUrlLookupEntry.getKey() + "__", namedUrlLookupEntry.getValue());
            }
        }

        String type = hyperlink.getType();
        String customTitle = null;
        if (type != null) {
            int index = type.indexOf("|");
            if (index != -1) {
                customTitle = type.substring(index + 1);
                type = type.substring(0, index);
            }
        }

        HyperlinkType hyperlinkType = null;
        if (type != null) {
            try {
                hyperlinkType = HyperlinkType.valueOf(type.toUpperCase());
            } catch (final RuntimeException e) {
                GWT.log("Could not parse open type value of " + type);
            }
        }

        if (hyperlinkType != null) {
            switch (hyperlinkType) {
                case DASHBOARD: {
                    ShowDashboardEvent.fire(this, href);
                    break;
                }
                case TAB: {
                    final IFrameContentPresenter presenter = iFrameContentPresenterProvider.get();
                    presenter.setUrl(hyperlink.getHref());
                    presenter.setCustomTitle(customTitle);
                    presenter.setIcon(hyperlink.getIcon());
                    contentManager.open(callback ->
                                    ConfirmEvent.fire(this,
                                            "Are you sure you want to close?",
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
                    final HandlerRegistration handlerRegistration = presenter.addDirtyHandler(event1 -> RenamePopupEvent.fire(this, presenter, presenter.getLabel()));
                    presenter.setUrl(hyperlink.getHref());
                    presenter.setCustomTitle(customTitle);

                    final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                        @Override
                        public void onHideRequest(final boolean autoClose, final boolean ok) {
                            HidePopupEvent.fire(HyperlinkEventHandlerImpl.this, presenter, autoClose, ok);
                        }

                        @Override
                        public void onHide(final boolean autoClose, final boolean ok) {
                            handlerRegistration.removeHandler();
                            presenter.close();
                        }
                    };

                    ShowPopupEvent.fire(this,
                            presenter,
                            PopupType.CLOSE_DIALOG,
                            null,
                            popupSize,
                            presenter.getLabel(),
                            popupUiHandlers,
                            null);
                    break;
                }
                case BROWSER: {
                    Window.open(href, "_blank", "");
                    break;
                }
                case STEPPING: {
                    final long id = getParam(href, "id", -1);
                    final long partNo = getParam(href, "partNo", 0);
                    final long recordNo = getParam(href, "recordNo", 0);
                    BeginPipelineSteppingEvent.fire(this, id, null, null, new StepLocation(id, partNo, recordNo), null);
                    break;
                }
                case DATA: {
                    final long id = getParam(href, "id", -1);
                    final long partNo = getParam(href, "partNo", 0);
                    final long recordNo = getParam(href, "recordNo", 0);
                    final int lineFrom = (int) getParam(href, "lineFrom", 1);
                    final int colFrom = (int) getParam(href, "colFrom", 0);
                    final int lineTo = (int) getParam(href, "lineTo", 1);
                    final int colTo = (int) getParam(href, "colTo", 0);
                    final SourceLocation sourceLocation = new SourceLocation(id, null, partNo, recordNo, new Highlight(new DefaultLocation(lineFrom, colFrom), new DefaultLocation(lineTo, colTo)));
                    ShowDataEvent.fire(this, sourceLocation);
                    break;
                }
                case ANNOTATION: {
                    final long metaId = getParam(href, "metaId", 0);
                    final long eventId = getParam(href, "eventId", 0);
                    ShowAnnotationEvent.fire(this, metaId, eventId);
                    break;
                }
                default:
                    Window.open(href, "_blank", "");
            }
        } else {
            Window.open(href, "_blank", "");
        }
    }

    private long getParam(final String href, final String paramName, final long def) {
        int start = href.indexOf(paramName + "=");
        if (start != -1) {
            start = start + (paramName + "=").length();
            int end = href.indexOf("&", start);
            if (end == -1) {
                return Long.parseLong(href.substring(start));
            } else {
                return Long.parseLong(href.substring(start, end));
            }
        }
        return def;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}

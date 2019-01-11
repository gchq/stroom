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
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.ContentManager;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

@Singleton
public class HyperlinkEventHandlerImpl extends HandlerContainerImpl implements HyperlinkEvent.Handler, HasHandlers {
    private final EventBus eventBus;
    private final Provider<IFrameContentPresenter> iFrameContentPresenterProvider;
    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;

//    private Map<String, String> namedUrls;

    @Inject
    public HyperlinkEventHandlerImpl(final EventBus eventBus,
                                     final Provider<IFramePresenter> iFramePresenterProvider,
                                     final Provider<IFrameContentPresenter> iFrameContentPresenterProvider,
                                     final ContentManager contentManager) {
        this.eventBus = eventBus;
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.iFrameContentPresenterProvider = iFrameContentPresenterProvider;
        this.contentManager = contentManager;

//        clientPropertyCache.get()
//                .onSuccess(result -> namedUrls = result.getLookupTable(ClientProperties.URL_LIST, ClientProperties.URL_BASE))
//                .onFailure(caught -> AlertEvent.fireError(HyperlinkEventHandlerImpl.this, caught.getMessage(), null));

        registerHandler(eventBus.addHandler(HyperlinkEvent.getType(), this));
    }

    @Override
    public void onLink(final HyperlinkEvent event) {
        final Hyperlink hyperlink = event.getHyperlink();

        String href = hyperlink.getHref();
//        if (namedUrls != null) {
//            for (final Map.Entry<String, String> namedUrlLookupEntry : namedUrls.entrySet()) {
//                href = href.replaceAll("__" + namedUrlLookupEntry.getKey() + "__", namedUrlLookupEntry.getValue());
//            }
//        }

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

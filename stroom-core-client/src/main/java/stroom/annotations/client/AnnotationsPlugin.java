package stroom.annotations.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.cell.clickable.client.HyperlinkTarget;
import stroom.core.client.ContentManager;
import stroom.core.client.MenuKeys;
import stroom.core.client.presenter.Plugin;
import stroom.menubar.client.event.BeforeRevealMenubarEvent;
import stroom.svg.client.SvgPresets;
import stroom.widget.iframe.client.presenter.IFramePresenter;
import stroom.widget.menu.client.presenter.IconMenuItem;

public class AnnotationsPlugin extends Plugin {

    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;

    @Inject
    public AnnotationsPlugin(final EventBus eventBus,
                             final Provider<IFramePresenter> iFramePresenterProvider,
                             final ContentManager contentManager) {
        super(eventBus);
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.contentManager = contentManager;
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
                            .href("http://192.168.0.26:3000/")
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

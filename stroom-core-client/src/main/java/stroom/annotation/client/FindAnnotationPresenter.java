package stroom.annotation.client;

import stroom.annotation.client.FindAnnotationPresenter.FindAnnotationProxy;
import stroom.annotation.shared.Annotation;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class FindAnnotationPresenter
        extends MyPresenter<FindView, FindAnnotationProxy>
        implements ShowFindAnnotationEvent.Handler, FindUiHandlers, FindDocResultListHandler<Annotation> {

    private boolean showing;
    private final FindAnnotationListPresenter findResultListPresenter;

    @Inject
    public FindAnnotationPresenter(final EventBus eventBus,
                                   final FindView view,
                                   final FindAnnotationProxy proxy,
                                   final FindAnnotationListPresenter findResultListPresenter) {
        super(eventBus, view, proxy);
        getView().setDialogMode(false);
        this.findResultListPresenter = findResultListPresenter;

        view.setResultView(findResultListPresenter.getView());
        view.setUiHandlers(this);
        findResultListPresenter.setFindResultListHandler(this);
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @ProxyEvent
    public void onShow(final ShowFindAnnotationEvent event) {
        if (!showing) {
            showing = true;

            // Make sure we are set to focus text next time we show.
            findResultListPresenter.setFocusText(true);

            // Refresh the results.
            findResultListPresenter.refresh();

            // Ensure list has a border in the popup view.
            findResultListPresenter.getView().asWidget().addStyleName("form-control-border form-control-background");

            getView().setDialogMode(true);
            final PopupSize popupSize = PopupSize.resizable(800, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Choose Annotation")
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            event.getAnnotationConsumer().accept(findResultListPresenter.getSelected());
                        }
                        e.hide();
                    })
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    @Override
    protected void revealInParent() {

    }

    @Override
    public void openDocument(final Annotation match) {
        HidePopupRequestEvent.builder(this).ok(true).fire();
    }

    @Override
    public void changeQuickFilter(final String name) {
        findResultListPresenter.setFilter(name);
        findResultListPresenter.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultListPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultListPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    @ProxyCodeSplit
    public interface FindAnnotationProxy extends Proxy<FindAnnotationPresenter> {

    }
}

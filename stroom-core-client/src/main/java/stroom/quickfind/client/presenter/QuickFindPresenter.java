package stroom.quickfind.client.presenter;

import stroom.quickfind.client.presenter.QuickFindPresenter.QuickFindView;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class QuickFindPresenter extends MyPresenterWidget<QuickFindView> {

    @Inject
    public QuickFindPresenter(final EventBus eventBus,
                              final QuickFindView view) {
        super(eventBus, view);
    }


    public void show() {

        final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    GWT.log("ok");
                } else {
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        final PopupSize popupSize = new PopupSize(
                640,
                480,
                true);

        ShowPopupEvent.fire(this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                popupSize,
                "Quick Finder",
                internalPopupUiHandlers);
    }

    private void hide() {
        HidePopupEvent.fire(
                QuickFindPresenter.this,
                QuickFindPresenter.this);
    }

    public interface QuickFindView extends View {

    }
}

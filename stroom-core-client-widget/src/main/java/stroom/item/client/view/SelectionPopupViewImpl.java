package stroom.item.client.view;

import stroom.item.client.SelectionBoxModel;
import stroom.item.client.SelectionPopup;
import stroom.item.client.presenter.SelectionPopupView;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class SelectionPopupViewImpl extends ViewImpl implements SelectionPopupView {

    @UiField(provided = true)
    SelectionPopup popup;

    private final Widget widget;

    public SelectionPopupViewImpl() {
        popup = new SelectionPopup();
        this.widget = popup;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void show(final PopupPosition position) {
        popup.show(position);
    }

    @Override
    public void hide() {
        popup.hide();
    }

    @Override
    public void setModel(final SelectionBoxModel<?> model) {
        popup.setModel(model);
    }

    @Override
    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popup.addCloseHandler(handler);
    }

    @Override
    public void addAutoHidePartner(final Element partner) {
        popup.addAutoHidePartner(partner);
    }
}

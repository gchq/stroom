package stroom.item.client.presenter;

import stroom.item.client.SelectionBoxModel;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.PopupPanel;
import com.gwtplatform.mvp.client.View;

public interface SelectionPopupView extends View {

    void addAutoHidePartner(Element partner);

    void setModel(SelectionBoxModel<?> model);

    void show(final PopupPosition position);

    void hide();

    HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler);
}

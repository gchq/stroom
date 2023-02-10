package stroom.item.client.presenter;

import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.PopupPanel;
import com.gwtplatform.mvp.client.View;

import java.util.Collection;

public interface AutocompletePopupView<R> extends View {

    void setVisibleItemCount(final int itemCount);

    void clearItems();

    void addItems(final Collection<R> list);

    R getSelectedObject();

    void clearSelection();

    void showPopup(final PopupPosition position);

    void hidePopup();

    HandlerRegistration addSelectionHandler(final SelectionHandler<R> handler);

    HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler);
}

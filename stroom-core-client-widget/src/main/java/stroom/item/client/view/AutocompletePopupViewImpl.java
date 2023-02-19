package stroom.item.client.view;

import stroom.docref.HasDisplayValue;
import stroom.item.client.AutocompletePopup;
import stroom.item.client.presenter.AutocompletePopupView;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Collection;

public class AutocompletePopupViewImpl<R extends HasDisplayValue> extends ViewImpl implements AutocompletePopupView<R> {

    @UiField(provided = true)
    AutocompletePopup<R> popup;

    private Widget widget;

    public AutocompletePopupViewImpl() {
        popup = new AutocompletePopup<>();

        setWidget(popup);
    }

    void setWidget(final Widget widget) {
        this.widget = widget;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setVisibleItemCount(final int itemCount) {
        popup.setVisibleItemCount(itemCount);
    }

    @Override
    public void clearItems() {
        popup.clear();
    }

    @Override
    public void addItems(final Collection<R> list) {
        popup.addItems(list);
    }

    @Override
    public R getSelectedObject() {
        return popup.getSelectedItem();
    }

    @Override
    public void clearSelection() {
        popup.clearSelection();
    }

    @Override
    public void showPopup(final PopupPosition position) {
        popup.show(position);
    }

    @Override
    public void hidePopup() {
        popup.hide();
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<R> handler) {
        return popup.addSelectionHandler(handler);
    }

    @Override
    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popup.addCloseHandler(handler);
    }
}

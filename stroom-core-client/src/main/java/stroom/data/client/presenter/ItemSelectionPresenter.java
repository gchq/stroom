package stroom.data.client.presenter;

import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.util.shared.HasItems;
import stroom.util.shared.RowCount;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ItemSelectionPresenter extends MyPresenterWidget<ItemSelectionView> {

    private HasItems display;

    @Inject
    public ItemSelectionPresenter(final EventBus eventBus,
                                  final ItemSelectionView view) {
        super(eventBus, view);
    }

    public void setDisplay(final HasItems display) {
        this.display = display;
    }

    private void write() {
        final long newItemNo = getView().getItemNo();
        if (newItemNo != display.getItemRange().getOffset()) {
            display.setItemNo(getView().getItemNo());
        }
    }

    private void read() {
        getView().setItemNo(display.getItemRange().getOffset());
        getView().setTotalItemsCount(display.getTotalItemsCount());
    }

    public void show(final PopupUiHandlers popupUiHandlers) {
        read();
        ShowPopupEvent.fire(
                this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                "Select " + display.getName(),
                popupUiHandlers);
    }

    public void hide(final boolean autoClose, final boolean ok) {
        if (ok) {
            write();
        }
        HidePopupEvent.fire(
                ItemSelectionPresenter.this,
                ItemSelectionPresenter.this,
                autoClose,
                ok);
    }

    public interface ItemSelectionView extends View {

        void setName(final String name);

        /**
         * Zero based
         */
        long getItemNo();

        /**
         * Zero based
         */
        void setItemNo(final long itemNo);

        void setTotalItemsCount(final RowCount<Long> totalItemsCount);
    }
}

package stroom.data.client.presenter;

import stroom.data.client.presenter.ItemSelectionPresenter.ItemSelectionView;
import stroom.util.shared.Count;
import stroom.util.shared.HasItems;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
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

    public void show() {
        read();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Select " + display.getName())
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        write();
                    }
                    e.hide();
                })
                .fire();
    }

    public interface ItemSelectionView extends View, Focus {

        void setName(final String name);

        /**
         * Zero based
         */
        long getItemNo();

        /**
         * Zero based
         */
        void setItemNo(final long itemNo);

        void setTotalItemsCount(final Count<Long> totalItemsCount);
    }
}

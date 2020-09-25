package stroom.data.client.presenter;

import stroom.data.client.presenter.CharacterRangeSelectionPresenter.CharacterRangeSelectionView;
import stroom.util.shared.DataRange;
import stroom.util.shared.RowCount;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CharacterRangeSelectionPresenter extends MyPresenterWidget<CharacterRangeSelectionView> {

    private DataRange dataRange;

    @Inject
    public CharacterRangeSelectionPresenter(final EventBus eventBus,
                                            final CharacterRangeSelectionView view) {
        super(eventBus, view);
    }

    public DataRange getDataRange() {
        return dataRange;
    }

    public void setDataRange(final DataRange dataRange) {
        this.dataRange = dataRange;
    }

    public void setTotalCharsCount(final RowCount<Long> totalCharsCount) {
        getView().setTotalCharsCount(totalCharsCount);
    }

    private void write() {
        dataRange = getView().getDataRange();
    }

    private void read() {
        getView().setDataRange(dataRange);
    }

    public void show(final PopupUiHandlers popupUiHandlers) {
        read();
        ShowPopupEvent.fire(
                this,
                this,
                PopupType.OK_CANCEL_DIALOG,
                "Set Source Range",
                popupUiHandlers);
    }

    public void hide(final boolean autoClose, final boolean ok) {
        if (ok) {
            write();
        }
        HidePopupEvent.fire(
                CharacterRangeSelectionPresenter.this,
                CharacterRangeSelectionPresenter.this,
                autoClose,
                ok);
    }

    public interface CharacterRangeSelectionView extends View {

        DataRange getDataRange();

        void setDataRange(final DataRange dataRange);

        void setTotalCharsCount(final RowCount<Long> totalCharCount);
    }
}

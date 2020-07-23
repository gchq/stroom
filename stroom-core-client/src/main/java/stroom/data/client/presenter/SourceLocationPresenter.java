package stroom.data.client.presenter;

import stroom.data.client.presenter.SourceLocationPresenter.SourceLocationView;
import stroom.pipeline.shared.SourceLocation;
import stroom.util.shared.RowCount;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class SourceLocationPresenter extends MyPresenterWidget<SourceLocationView> {

    private SourceLocation sourceLocation;

    @Inject
    public SourceLocationPresenter(final EventBus eventBus,
                                   final SourceLocationView view) {
        super(eventBus, view);
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public void setIdEnabled(final boolean isEnabled) {
        getView().setIdEnabled(isEnabled);
    }

    public void setPartNoEnabled(final boolean isEnabled) {
        getView().setPartNoEnabled(isEnabled);
    }

    public void setSegmentNoEnabled(final boolean isEnabled) {
        getView().setSegmentNoEnabled(isEnabled);
    }

    public void setSegmentNoVisible(final boolean isVisible) {
        getView().setSegmentNoVisible(isVisible);
    }

    public void setPartsCount(final RowCount<Long> partsCount) {
        getView().setPartCount(partsCount);
    }

    public void setSegmentsCount(final RowCount<Long> segmentsCount) {
        getView().setSegmentsCount(segmentsCount);
    }

    public void setTotalCharsCount(final RowCount<Long> totalCharsCount) {
        getView().setTotalCharsCount(totalCharsCount);
    }

    private void write() {
        sourceLocation = getView().getSourceLocation();
    }

    private void read() {
        getView().setSourceLocation(sourceLocation);
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
                SourceLocationPresenter.this,
                SourceLocationPresenter.this,
                autoClose,
                ok);
    }

    public interface SourceLocationView extends View {

        SourceLocation getSourceLocation();

        void setSourceLocation(final SourceLocation sourceLocation);

        void setIdEnabled(final boolean isEnabled);

        void setPartNoEnabled(final boolean isEnabled);

        void setSegmentNoEnabled(final boolean isEnabled);

        void setSegmentNoVisible(final boolean isVisible);

        void setPartCount(final RowCount<Long> partCount);

        void setSegmentsCount(final RowCount<Long> segmentCount);

        void setTotalCharsCount(final RowCount<Long> totalCharCount);
    }
}

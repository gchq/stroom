package stroom.widget.progress.client.view;

import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ProgressViewImpl extends ViewImpl implements ProgressView {

    private final Widget widget;

    @UiField
    FlowPanel progressBarInner;

    @Inject
    public ProgressViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setRangeFromPct(final double rangeFrom) {
        progressBarInner.getElement()
                .getStyle()
                .setLeft(rangeFrom, Unit.PCT);
    }

    @Override
    public void setProgressPct(final double progress) {
        progressBarInner.getElement()
                .getStyle()
                .setWidth(progress, Unit.PCT);
    }

    @Override
    public void setProgressBarColour(final String colour) {
        progressBarInner.getElement()
                .getStyle()
                .setBackgroundColor(colour);
    }

    @Override
    public void setTitle(final String title) {
        progressBarInner.getElement()
                .setTitle(title);
    }

    @Override
    public void setVisible(final boolean isVisible) {

    }

    public interface Binder extends UiBinder<Widget, ProgressViewImpl> {
    }
}

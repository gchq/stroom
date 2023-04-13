package stroom.data.client.view;

import stroom.data.client.presenter.SourcePresenter.SourceView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceViewImpl extends ViewImpl implements SourceView {

    private final Widget widget;

    @UiField
    Label lblFeed;
    @UiField
    Label lblId;
    @UiField
    Label lblPartNo;
    @UiField
    Label lblSegmentNo;
    @UiField
    Label lblType;
    @UiField
    FlowPanel container;
    @UiField
    SimplePanel navigatorContainer;
    @UiField
    SimplePanel progressBarPanel;

    @Inject
    public SourceViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTitle(final String feedName,
                         final long id,
                         final long partNo,
                         final long segmentNo,
                         final String type) {
        lblFeed.setText(feedName);
        lblId.setText(Long.toString(id));
        lblPartNo.setText(Long.toString(partNo + 1));
        lblSegmentNo.setText(Long.toString(segmentNo + 1));
        lblType.setText(type);
    }

    @Override
    public void setTextView(final View textView) {
        container.add(textView.asWidget());
    }

    @Override
    public void setNavigatorView(final View characterNavigatorView) {
        if (characterNavigatorView != null) {
            navigatorContainer.setWidget(characterNavigatorView.asWidget());
        } else {
            navigatorContainer.clear();
        }
    }

    @Override
    public void setProgressView(final View progressView) {
        if (progressView != null) {
            progressBarPanel.setWidget(progressView.asWidget());
        } else {
            progressBarPanel.clear();
        }
    }

    public interface Binder extends UiBinder<Widget, SourceViewImpl> {

    }
}

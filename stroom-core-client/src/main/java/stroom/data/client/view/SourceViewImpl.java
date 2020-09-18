package stroom.data.client.view;

import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.pager.client.DataNavigator;
import stroom.pipeline.shared.SourceLocation;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewImpl;

public class SourceViewImpl extends ViewImpl implements SourceView {

    private SourceLocation sourceLocation;
    private Widget widget;

    @Inject
    ResizeSimplePanel container;
    @Inject
    DataNavigator dataNavigator;
    @Inject
    ButtonPanel buttonPanel;

    @Inject
    public SourceViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void addToSlot(final Object slot, final Widget content) {

    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    public void setTextView(final TextView textView) {
        this.container.setWidget(textView.asWidget());
    }

    public interface Binder extends UiBinder<Widget, SourceViewImpl> {
    }
}

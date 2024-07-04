package stroom.node.client.view;

import stroom.node.client.presenter.NodePresenter;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class NodeViewImpl extends ViewImpl implements NodePresenter.NodeView {

    private final Widget widget;
    @UiField
    SimplePanel nodeList;
    @UiField
    SimplePanel nodeJobList;

    @Inject
    public NodeViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (NodePresenter.NODE_LIST.equals(slot)) {
            nodeList.setWidget(content);
        } else if (NodePresenter.NODE_JOB_LIST.equals(slot)) {
            nodeJobList.setWidget(content);
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, NodeViewImpl> {

    }
}

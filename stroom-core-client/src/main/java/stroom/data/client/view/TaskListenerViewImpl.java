package stroom.data.client.view;

import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class TaskListenerViewImpl extends ViewImpl implements TaskListenerView {

    private final FlowPanel panel;
    private final SpinnerLarge spinner;

    public TaskListenerViewImpl() {
        panel = new FlowPanel();
        spinner = new SpinnerLarge();
        spinner.addStyleName("spinner-center");
        spinner.setVisible(false);
        panel.add(spinner);
    }

    @Override
    public Widget asWidget() {
        return panel;
    }

    @Override
    public void incrementTaskCount() {
        spinner.incrementTaskCount();
    }

    @Override
    public void decrementTaskCount() {
        spinner.decrementTaskCount();
    }

    @Override
    public void setChildView(final View childView) {
        panel.add(childView.asWidget());
    }
}

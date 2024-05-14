package stroom.data.pager.client;

import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskListener;
import stroom.widget.button.client.SvgButton;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public class RefreshButton
        extends Composite
        implements TaskListener {

    private final SvgButton button;
    private int taskCount;

    public RefreshButton() {
        final SimplePanel refreshInner = new SimplePanel();
        refreshInner.setStyleName("refresh-inner");
        refreshInner.getElement().setInnerHTML(SvgImage.REFRESH.getSvg());
        final SimplePanel refreshOuter = new SimplePanel(refreshInner);
        refreshOuter.setStyleName("refresh-outer");

        final SimplePanel spinnerInner = new SimplePanel();
        spinnerInner.setStyleName("spinner-inner");
        spinnerInner.getElement().setInnerHTML(SvgImage.SPINNER_SPINNER.getSvg());
        final SimplePanel spinnerOuter = new SimplePanel(spinnerInner);
        spinnerOuter.setStyleName("spinner-outer");

        final SimplePanel spinningInner = new SimplePanel();
        spinningInner.setStyleName("spinning-inner");
        spinningInner.getElement().setInnerHTML(SvgImage.SPINNER_SPINNING.getSvg());
        final SimplePanel spinningOuter = new SimplePanel(spinningInner);
        spinningOuter.setStyleName("spinning-outer");

        final SimplePanel pauseInner = new SimplePanel();
        pauseInner.setStyleName("pause-inner");
        pauseInner.getElement().setInnerHTML(SvgImage.SPINNER_PAUSE.getSvg());
        final SimplePanel pauseOuter = new SimplePanel(pauseInner);
        pauseOuter.setStyleName("pause-outer");

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName("background refreshButton");
        layout.add(refreshOuter);
        layout.add(spinnerOuter);
        layout.add(spinningOuter);
        layout.add(pauseOuter);

        button = new SvgButton();
        button.addStyleName("refreshButton");
        button.setTitle("Refresh");
        button.setEnabled(true);

        button.getElement().removeAllChildren();
        DOM.appendChild(button.getElement(), layout.getElement());

        initWidget(button);
    }

    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            button.addStyleName("refreshing");
        } else {
            button.removeStyleName("refreshing");
        }
    }

    public void setAllowPause(final boolean allowPause) {
        if (allowPause) {
            button.addStyleName("allowPause");
        } else {
            button.removeStyleName("allowPause");
        }
    }

    public void setPaused(final boolean paused) {
        if (paused) {
            button.setTitle("Resume Update");
            button.addStyleName("paused");
        } else {
            button.setTitle("Pause Update");
            button.removeStyleName("paused");
        }
    }

    public void setEnabled(final boolean enabled) {
        button.setEnabled(enabled);
    }

    public HandlerRegistration addClickHandler(ClickHandler handler) {
        return button.addClickHandler(handler);
    }

    @Override
    public void incrementTaskCount() {
        taskCount++;
        setRefreshing(taskCount > 0);
    }

    @Override
    public void decrementTaskCount() {
        taskCount--;
        setRefreshing(taskCount > 0);
    }
}

package stroom.data.pager.client;

import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskListener;
import stroom.widget.button.client.SvgButton;

import com.google.gwt.core.client.GWT;
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
    private boolean allowPause;
    private boolean paused;
    private boolean refreshing;
    private boolean refreshState;

    public RefreshButton() {
        final SimplePanel refreshInner = new SimplePanel();
        refreshInner.setStyleName("refresh-inner");
        refreshInner.getElement().setInnerHTML(SvgImage.REFRESH.getSvg());
        final SimplePanel refreshOuter = new SimplePanel(refreshInner);
        refreshOuter.setStyleName("refresh-outer");

        final SimplePanel circleInner = new SimplePanel();
        circleInner.setStyleName("circle-inner");
        circleInner.getElement().setInnerHTML(SvgImage.SPINNER_CIRCLE.getSvg());
        final SimplePanel circleOuter = new SimplePanel(circleInner);
        circleOuter.setStyleName("circle-outer");

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
        layout.setStyleName("background");
        layout.add(refreshOuter);
        layout.add(circleOuter);
        layout.add(spinningOuter);
        layout.add(pauseOuter);

        button = new SvgButton();
        button.addStyleName("RefreshButton");
        button.setTitle("Refresh");
        button.setEnabled(true);

        button.getElement().removeAllChildren();
        DOM.appendChild(button.getElement(), layout.getElement());

        initWidget(button);
    }

    public void setRefreshing(final boolean refreshing) {
        this.refreshing = refreshing;
        updateRefreshState();
    }

    public void setAllowPause(final boolean allowPause) {
        this.allowPause = allowPause;
        if (allowPause) {
            setEnabled(false);
            button.addStyleName("allowPause");
        } else {
            button.removeStyleName("allowPause");
        }

        update();
    }

    public void setPaused(final boolean paused) {
        this.paused = paused;
        if (paused) {
            button.setTitle("Resume Update");
            button.addStyleName("paused");
        } else {
            button.setTitle("Pause Update");
            button.removeStyleName("paused");
        }

        update();
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
        updateRefreshState();
    }

    @Override
    public void decrementTaskCount() {
        taskCount--;

        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        updateRefreshState();
    }

    public void updateRefreshState() {
        final boolean refreshState = refreshing || taskCount > 0;
        if (refreshState != this.refreshState) {
            this.refreshState = refreshState;
            if (refreshState) {
                button.addStyleName("refreshing");
            } else {
                button.removeStyleName("refreshing");
            }
            update();
        }
    }

    private void update() {
        if (allowPause && !paused) {
            setEnabled(refreshState);
        }
    }
}

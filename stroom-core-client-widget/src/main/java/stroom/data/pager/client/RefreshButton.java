/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.pager.client;

import stroom.svg.shared.SvgImage;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
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
        implements TaskMonitorFactory {

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
            button.addStyleName("paused");
        } else {
            button.removeStyleName("paused");
        }
        update();
    }

    public void setEnabled(final boolean enabled) {
        button.setEnabled(enabled);
    }

    public HandlerRegistration addClickHandler(final ClickHandler handler) {
        return button.addClickHandler(handler);
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                updateRefreshState();
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;
                updateRefreshState();
            }
        };
    }

    public void updateRefreshState() {
        if (taskCount < 0) {
            GWT.log("Negative task count");
        }

        final boolean refreshState = refreshing || taskCount > 0;
        if (refreshState != this.refreshState) {
            this.refreshState = refreshState;
            if (refreshState) {
                button.addStyleName("refreshing");
            } else {
                button.removeStyleName("refreshing");
            }
        }
        update();
    }

    private void update() {
        if (allowPause) {
            setEnabled(paused || refreshing || taskCount > 0);

            if (paused) {
                button.setTitle("Resume Update");
            } else if (refreshing || taskCount > 0) {
                button.setTitle("Pause Update");
            } else {
                button.setTitle("Not Updating");
            }
        } else {
            button.setTitle("Refresh");
        }
    }
}

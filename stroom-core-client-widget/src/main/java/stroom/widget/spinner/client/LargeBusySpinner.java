/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.spinner.client;

import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class LargeBusySpinner extends Composite implements TaskMonitorFactory {

    private static final Binder uiBinder = GWT.create(Binder.class);
    // What a screen reader is told while the spinner shows
    private static final String BUSY_TEXT = "Loading...";
    // Its BEM modifiers (busy-spinner.css): shown, and fading slowly
    private static final String VISIBLE = "busy-spinner--active";
    private static final String SOFT = "busy-spinner--soft";
    /// The modifier that centres the spinner over what is loading, e.g. in UiBinder
    /// `addStyleNames="busy-spinner--centred"`.
    public static final String CENTRED = "busy-spinner--centred";

    // The text of the spinner's status (a live region), empty while it is hidden. It is put in only
    // once the spinner shows, as a live region that appears with its text already in it isn't
    // reliably read
    @UiField
    SpanElement status;

    private int taskCount;
    private boolean visible = true;
    private final Timer hideTimer;

    public LargeBusySpinner() {
        initWidget(uiBinder.createAndBindUi(this));
        // Shown until hidden
        status.setInnerText(BUSY_TEXT);
        hideTimer = new Timer() {
            @Override
            public void run() {
                LargeBusySpinner.super.setVisible(false);
            }
        };
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                setVisible(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;

                if (taskCount < 0) {
                    GWT.log("Negative task count");
                }

                setVisible(taskCount > 0);
            }
        };
    }

    @Override
    public void setVisible(final boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            hideTimer.cancel();
            if (visible) {
                super.setVisible(visible);
                Scheduler.get().scheduleDeferred(() -> {
                    addStyleName(VISIBLE);
                    if (this.visible) {
                        status.setInnerText(BUSY_TEXT);
                    }
                });
            } else {
                status.setInnerText("");
                removeStyleName(VISIBLE);
                hideTimer.schedule(500);
            }
        }
    }

    public void setSoft(final boolean soft) {
        if (soft) {
            addStyleName(SOFT);
        } else {
            removeStyleName(SOFT);
        }
    }

    interface Binder extends UiBinder<Widget, LargeBusySpinner> {

    }
}

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

package stroom.widget.spinner.client;

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class SpinnerLarge extends Composite implements TaskMonitorFactory {

    private static final Binder uiBinder = GWT.create(Binder.class);

    private int taskCount;
    private boolean visible = true;
    private final Timer hideTimer;

    public SpinnerLarge() {
        initWidget(uiBinder.createAndBindUi(this));
        hideTimer = new Timer() {
            @Override
            public void run() {
                SpinnerLarge.super.setVisible(false);
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
                Scheduler.get().scheduleDeferred(() -> addStyleName("spinner__visible"));
            } else {
                removeStyleName("spinner__visible");
                hideTimer.schedule(500);
            }
        }
    }

    public void setSoft(final boolean soft) {
        if (soft) {
            addStyleName("soft");
        } else {
            removeStyleName("soft");
        }
    }

    interface Binder extends UiBinder<Widget, SpinnerLarge> {

    }
}

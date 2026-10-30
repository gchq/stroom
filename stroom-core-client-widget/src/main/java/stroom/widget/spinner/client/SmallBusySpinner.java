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

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

/// A small spinner shown while tasks run, e.g. on a tab or the explorer's bar. It fades in and out
/// (`busy-spinner--active`), and spins only while it is shown or fading out
/// (`busy-spinner--spinning`): once it has faded
/// out it is hidden (from screen readers too) and its animation is stopped, as the explorer's bar and
/// every tab have one, which would otherwise spin unseen all the time (#5299).
public class SmallBusySpinner extends Composite implements TaskMonitorFactory {

    private static final Binder BINDER = GWT.create(Binder.class);

    // How long it takes to fade out (busy-spinner.css), after which it stops spinning
    private static final int FADE_MILLIS = 500;
    private static final String BLOCK = "busy-spinner";
    private static final String ACTIVE = BLOCK + "--active";
    private static final String SPINNING = BLOCK + "--spinning";

    private int taskCount;
    private Timer stopSpinning;

    public SmallBusySpinner() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the spinner, fading it in, or fades it out and then stops it spinning.
    ///
    /// @param refreshing Whether something is loading.
    public void setRefreshing(final boolean refreshing) {
        if (stopSpinning != null) {
            stopSpinning.cancel();
            stopSpinning = null;
        }
        if (refreshing) {
            addStyleName(SPINNING);
            addStyleName(ACTIVE);
        } else if (getStyleName().contains(ACTIVE)) {
            removeStyleName(ACTIVE);
            stopSpinning = new Timer() {
                @Override
                public void run() {
                    stopSpinning = null;
                    removeStyleName(SPINNING);
                }
            };
            stopSpinning.schedule(FADE_MILLIS);
        }
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {
                taskCount++;
                setRefreshing(taskCount > 0);
            }

            @Override
            public void onEnd(final Task task) {
                taskCount--;

                if (taskCount < 0) {
                    GWT.log("Negative task count");
                }

                setRefreshing(taskCount > 0);
            }
        };
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SmallBusySpinner> {

    }
}

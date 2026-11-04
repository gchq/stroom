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

package stroom.widget.tab.client;

import stroom.widget.task.client.Task;
import stroom.widget.task.client.TaskMonitor;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Composite;

import java.util.function.Consumer;

/// A tab of a tab bar, a component with a template whose root is the tab (`role="tab"`).
public abstract class AbstractTab extends Composite implements TaskMonitorFactory {

    private boolean hidden;

    protected abstract void setText(String text);

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(final boolean hidden) {
        if (this.hidden != hidden) {
            this.hidden = hidden;
        }
    }

    protected Element getCloseElement() {
        return null;
    }

    /// @return Whether the tab shows that what it holds is loading; never, unless it has a spinner.
    public boolean isBusy() {
        return false;
    }

    /// @param onBusyChange Told when the tab starts or stops showing that what it holds is loading
    ///                     (true when it starts), e.g. for its bar to tell screen readers; or null.
    public void setOnBusyChange(final Consumer<Boolean> onBusyChange) {
        // A tab with no spinner never shows it
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return new TaskMonitor() {
            @Override
            public void onStart(final Task task) {

            }

            @Override
            public void onEnd(final Task task) {

            }
        };
    }
}

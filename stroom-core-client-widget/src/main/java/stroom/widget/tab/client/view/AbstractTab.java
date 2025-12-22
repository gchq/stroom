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

package stroom.widget.tab.client.view;

import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

public abstract class AbstractTab extends Widget implements TaskMonitorFactory {

    private boolean hidden;

    protected abstract void setKeyboardSelected(boolean selected);

    protected abstract void setSelected(boolean selected);

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

    protected void setCloseActive(final boolean active) {
    }

    protected void setHover(final boolean hover) {
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

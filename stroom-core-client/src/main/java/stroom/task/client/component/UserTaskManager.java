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

package stroom.task.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// The user's running tasks, one [UserTask] each, scrolling in a frame.
public class UserTaskManager extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlowPanel taskList;

    /// Makes an empty list.
    public UserTaskManager() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Focuses the list.
    public void focus() {
        taskList.getElement().focus();
    }

    /// @param task A task to show, after the others.
    public void addTask(final UserTask task) {
        taskList.add(task);
    }

    /// @param task A task shown, to remove.
    public void removeTask(final UserTask task) {
        taskList.remove(task);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserTaskManager> {

    }
}

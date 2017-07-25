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

package stroom.jobsystem.client;

import com.google.gwt.cell.client.AbstractInputCell;
import com.google.gwt.cell.client.SelectionCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.jobsystem.shared.TaskType;

import java.util.HashMap;

public class TaskTypeCell extends AbstractInputCell<TaskType, TaskType> {
    private final JobType[] options;
    private String button = null;
    private HashMap<JobType, Integer> indexForOption = new HashMap<JobType, Integer>();

    /**
     * Construct a new {@link SelectionCell} with the specified options.
     *
     * @param options the options in the cell
     */
    public TaskTypeCell() {
        super("change", "click");
        if (button == null) {
            button = "<img style=\"width:16px;height:16px\" src=\"images/history.svg\"/>";
        }
        this.options = JobType.values();
        int index = 0;
        for (JobType option : options) {
            indexForOption.put(option, index++);
        }
    }

    @Override
    public void onBrowserEvent(Context context, Element parent, TaskType value, NativeEvent event,
                               ValueUpdater<TaskType> valueUpdater) {
        if (value != null) {
            super.onBrowserEvent(context, parent, value, event, valueUpdater);
            final String type = event.getType();

            if ("change".equals(type)) {
                Object key = context.getKey();
                SelectElement select = parent.getFirstChild().getFirstChild().cast();
                JobType newValue = options[select.getSelectedIndex()];
                TaskType newTaskType = new TaskType(newValue, value.getSchedule());
                setViewData(key, newTaskType);

                final Node div = parent.getFirstChild();
                if (div != null) {
                    if (JobType.DISTRIBUTED.equals(newValue)) {
                        if (div.getFirstChild() != null && div.getFirstChild().getNextSibling() != null) {
                            div.removeChild(div.getFirstChild().getNextSibling());
                        }
                    } else {
                        if (div.getFirstChild() != null && div.getFirstChild().getNextSibling() == null) {
                            String html = parent.getFirstChildElement().getInnerHTML();
                            html += button;
                            parent.getFirstChildElement().setInnerHTML(html);
                        }
                    }
                }

                finishEditing(parent, newTaskType, key, valueUpdater);
                if (valueUpdater != null) {
                    valueUpdater.update(newTaskType);
                }
            }
        }
    }

    @Override
    protected void onEnterKeyDown(Context context, Element parent, TaskType value, NativeEvent event,
                                  ValueUpdater<TaskType> valueUpdater) {
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(Context context, TaskType value, SafeHtmlBuilder sb) {
        if (value != null) {
            // Get the view data.
            Object key = context.getKey();
            TaskType viewData = getViewData(key);
            if (viewData != null && viewData.equals(value)) {
                clearViewData(key);
                viewData = null;
            }

            sb.appendHtmlConstant("<span style=\"position:relative;width:120px;height:16px;\">");
            if (JobType.CRON.equals(value.getJobType())) {
                sb.appendHtmlConstant("Cron " + value.getSchedule() + " ");
            }
            if (JobType.FREQUENCY.equals(value.getJobType())) {
                sb.appendHtmlConstant("Frequency " + value.getSchedule() + " ");
            }
            if (JobType.DISTRIBUTED.equals(value.getJobType())) {
                sb.appendHtmlConstant("Distributed ");
            }

            if (JobType.CRON.equals(value.getJobType()) || JobType.FREQUENCY.equals(value.getJobType())) {
                sb.appendHtmlConstant(button);
            }
            sb.appendHtmlConstant("</div>");
        }
    }
}

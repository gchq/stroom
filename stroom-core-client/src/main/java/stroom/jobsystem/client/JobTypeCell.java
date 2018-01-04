/*
 * Copyright 2018 Crown Copyright
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
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.jobsystem.shared.JobNode.JobType;

public class JobTypeCell extends AbstractInputCell<JobType, JobType> {
    private String button = null;

    /**
     * Construct a new {@link SelectionCell} with the specified options.
     */
    public JobTypeCell() {
        super("change", "click");
        if (button == null) {
            button = "<img style=\"width:16px;height:16px;cursor:pointer\" src=\"images/history.svg\"/>";
        }
    }

    @Override
    protected void onEnterKeyDown(Context context, Element parent, JobType value, NativeEvent event,
                                  ValueUpdater<JobType> valueUpdater) {
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    @Override
    public void render(Context context, JobType value, SafeHtmlBuilder sb) {
        if (value != null) {
            if (JobType.CRON.equals(value) || JobType.FREQUENCY.equals(value)) {
                sb.appendHtmlConstant(button);
            }
        }
    }
}

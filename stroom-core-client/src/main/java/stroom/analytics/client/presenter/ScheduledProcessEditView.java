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

package stroom.analytics.client.presenter;

import stroom.schedule.client.ScheduleBox;
import stroom.widget.datepicker.client.DateTimeBox;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public interface ScheduledProcessEditView extends View,
        HasUiHandlers<ProcessingStatusUiHandlers>,
        Focus {

    String getName();

    void setName(String name);

    boolean isEnabled();

    void setEnabled(final boolean enabled);

    void setNodes(List<String> nodes);

    String getNode();

    void setNode(String node);

    ScheduleBox getScheduleBox();

    DateTimeBox getStartTime();

    DateTimeBox getEndTime();

    void setRunAsUserView(View view);
}

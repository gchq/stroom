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

package stroom.data.grid.client;

import stroom.data.pager.client.RefreshButton;
import stroom.svg.client.Preset;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ToggleButtonView;

import com.google.gwt.user.cellview.client.AbstractHasData;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;

public interface PagerView extends View, TaskMonitorFactory {

    ButtonView addButton(Preset preset);

    void addButton(ButtonView buttonView);

    ToggleButtonView addToggleButton(Preset primaryPreset,
                                     Preset secondaryPreset);

    RefreshButton getRefreshButton();

    void setDataWidget(final AbstractHasData<?> widget);

    void setPagerVisible(boolean visible);

    MessagePanel getMessagePanel();
}

/*
 * Copyright 2021 Crown Copyright
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

package stroom.job.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Jobs screen: the jobs, and the selected job on each node.
public class Jobs extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel jobList;
    @UiField
    SimplePanel jobNodeList;

    /// Makes the screen, with no content until it is given it.
    public Jobs() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the list of jobs.
    ///
    /// @param view Its view.
    public void setJobListView(final IsWidget view) {
        jobList.setWidget(view.asWidget());
    }

    /// Shows the selected job on each node.
    ///
    /// @param view Its view.
    public void setJobNodeListView(final IsWidget view) {
        jobNodeList.setWidget(view.asWidget());
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, Jobs> {

    }
}

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

package stroom.dashboard.client.component;

import stroom.dashboard.client.flexlayout.FlexLayout;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A dashboard's body: its layout of panes, which draws itself for design mode while the dashboard is
/// designed.
public class DashboardBody extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FlexLayout layout;

    /// Makes the body, with an empty layout.
    public DashboardBody() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The layout of panes, for the controller to fill.
    public FlexLayout getLayout() {
        return layout;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DashboardBody> {

    }
}

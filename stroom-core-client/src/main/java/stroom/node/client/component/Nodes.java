/*
 * Copyright 2024 Crown Copyright
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

package stroom.node.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Nodes screen: the nodes, and the selected node's jobs.
public class Nodes extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    NodeStatusList nodeList;
    @UiField
    NodeJobList nodeJobList;

    /// Makes the screen, with its lists empty until their controllers fill them.
    public Nodes() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The nodes, which its controller attaches the list's controller to.
    public NodeStatusList getNodeList() {
        return nodeList;
    }

    /// @return The selected node's jobs, which its controller attaches the list's controller to.
    public NodeJobList getNodeJobList() {
        return nodeJobList;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, Nodes> {

    }
}

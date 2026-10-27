/*
 * Copyright 2020 Crown Copyright
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

package stroom.config.global.client.component;

import stroom.config.global.client.presenter.NodeSource;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;
import java.util.Set;

/// The content of a property's Cluster Values: its effective values, each with the nodes that
/// have it.
public class ConfigPropertyClusterValues extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ConfigPropertyClusterValuesList list;

    /// Makes empty content.
    public ConfigPropertyClusterValues() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param effectiveValueToNodesMap The effective values, each with the nodes that have it.
    public void setData(final Map<String, Set<NodeSource>> effectiveValueToNodesMap) {
        list.setData(effectiveValueToNodesMap);
    }

    /// Puts the focus on the list.
    public void focus() {
        list.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ConfigPropertyClusterValues> {

    }
}

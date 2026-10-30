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

package stroom.index.client.component;

import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// An index volume: its node, path, state and byte limit.
public class IndexVolumeEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<String> nodeName;
    @UiField
    TextBox path;
    @UiField
    SelectionBox<VolumeUseState> state;
    @UiField
    TextBox bytesLimit;

    /// Makes an empty form, offering every state, with no nodes until [#setNodeNames(List)].
    public IndexVolumeEditor() {
        initWidget(BINDER.createAndBindUi(this));
        state.addItems(VolumeUseState.values());
    }

    /// Puts the focus on the node.
    public void focus() {
        nodeName.focus();
    }

    /// @param nodeNames The nodes the volume can belong to.
    public void setNodeNames(final List<String> nodeNames) {
        nodeName.clear();
        nodeName.addItems(nodeNames);
    }

    /// @param nodeName The node the volume belongs to.
    public void setNodeName(final String nodeName) {
        this.nodeName.setValue(nodeName);
    }

    /// @return The node the volume belongs to.
    public String getNodeName() {
        return nodeName.getValue();
    }

    /// @param path The directory the index shards are stored in.
    public void setPath(final String path) {
        this.path.setText(path);
    }

    /// @return The directory the index shards are stored in.
    public String getPath() {
        return path.getText();
    }

    /// @param state How the volume is used.
    public void setState(final VolumeUseState state) {
        this.state.setValue(state);
    }

    /// @return How the volume is used.
    public VolumeUseState getState() {
        return state.getValue();
    }

    /// @param byteLimit The byte limit, formatted with a unit, or blank for none.
    public void setByteLimit(final String byteLimit) {
        bytesLimit.setText(byteLimit);
    }

    /// @return The byte limit as typed.
    public String getByteLimit() {
        return bytesLimit.getText();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexVolumeEditor> {

    }
}

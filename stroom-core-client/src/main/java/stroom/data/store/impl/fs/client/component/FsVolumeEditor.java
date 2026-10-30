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

package stroom.data.store.impl.fs.client.component;

import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.editor.client.component.CodeEditor;
import stroom.item.client.SelectionBox;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

/// A data volume: its type, path, status, byte limit and, for S3 volumes, its configuration.
public class FsVolumeEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<FsVolumeType> volumeType;
    @UiField
    TextBox path;
    @UiField
    SelectionBox<VolumeUseStatus> status;
    @UiField
    TextBox byteLimit;
    @UiField
    CodeEditor configEditor;

    /// Makes an empty form, offering every volume type, in display order, and every status.
    public FsVolumeEditor() {
        initWidget(BINDER.createAndBindUi(this));
        volumeType.addItems(Arrays.stream(FsVolumeType.values())
                .sorted(Comparator.comparingInt(FsVolumeType::getDisplayIndex))
                .collect(Collectors.toList()));
        status.addItems(VolumeUseStatus.values());
    }

    /// Puts the focus on the path.
    public void focus() {
        path.setFocus(true);
    }

    /// @return The S3 configuration's editor, for its controller to attach to.
    public CodeEditor getConfigEditor() {
        return configEditor;
    }

    /// @param volumeType The volume's type.
    public void setVolumeType(final FsVolumeType volumeType) {
        this.volumeType.setValue(volumeType);
    }

    /// @return The volume's type.
    public FsVolumeType getVolumeType() {
        return volumeType.getValue();
    }

    /// @param path The directory the data is stored in.
    public void setPath(final String path) {
        this.path.setText(path);
    }

    /// @return The directory the data is stored in.
    public String getPath() {
        return path.getText();
    }

    /// @param status How the volume can be used.
    public void setStatus(final VolumeUseStatus status) {
        this.status.setValue(status);
    }

    /// @return How the volume can be used.
    public VolumeUseStatus getStatus() {
        return status.getValue();
    }

    /// @param byteLimit The byte limit, formatted with a unit, or blank for none.
    public void setByteLimit(final String byteLimit) {
        this.byteLimit.setText(byteLimit);
    }

    /// @return The byte limit as typed.
    public String getByteLimit() {
        return byteLimit.getText();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FsVolumeEditor> {

    }
}

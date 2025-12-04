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

package stroom.data.store.impl.fs.client.view;

import stroom.data.store.impl.fs.client.presenter.FsVolumeEditPresenter.FsVolumeEditView;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.item.client.SelectionBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

public class FsVolumeEditViewImpl extends ViewImpl implements FsVolumeEditView {

    private final Widget widget;

    @UiField
    SelectionBox<FsVolumeType> volumeType;
    @UiField
    TextBox path;
    @UiField
    SelectionBox<VolumeUseStatus> status;
    @UiField
    TextBox byteLimit;
    @UiField
    SimplePanel configEditorContainer;

    @Inject
    public FsVolumeEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        path.setFocus(true);
    }

    @Override
    public SelectionBox<FsVolumeType> getVolumeType() {
        return volumeType;
    }

    @Override
    public HasText getPath() {
        return path;
    }

    @Override
    public SelectionBox<VolumeUseStatus> getVolumeStatus() {
        return status;
    }

    @Override
    public HasText getByteLimit() {
        return byteLimit;
    }

    @Override
    public void setConfigView(final View view) {
        configEditorContainer.setWidget(view.asWidget());
    }

    public interface Binder extends UiBinder<Widget, FsVolumeEditViewImpl> {

    }
}

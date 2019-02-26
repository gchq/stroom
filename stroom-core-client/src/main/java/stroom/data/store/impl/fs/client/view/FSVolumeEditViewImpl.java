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

package stroom.data.store.impl.fs.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.data.store.impl.fs.client.presenter.FSVolumeEditPresenter.VolumeEditView;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.item.client.ItemListBox;

public class FSVolumeEditViewImpl extends ViewImpl implements VolumeEditView {
    private final Widget widget;
    @UiField
    TextBox path;
    @UiField
    ItemListBox<VolumeUseStatus> status;
    @UiField
    TextBox byteLimit;

    @Inject
    public FSVolumeEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HasText getPath() {
        return path;
    }

    @Override
    public ItemListBox<VolumeUseStatus> getStatus() {
        return status;
    }

    @Override
    public HasText getByteLimit() {
        return byteLimit;
    }

    public interface Binder extends UiBinder<Widget, FSVolumeEditViewImpl> {
    }
}

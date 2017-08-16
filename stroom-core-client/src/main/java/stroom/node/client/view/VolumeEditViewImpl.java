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

package stroom.node.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.node.client.presenter.VolumeEditPresenter.VolumeEditView;
import stroom.node.shared.Node;
import stroom.node.shared.Volume.VolumeType;
import stroom.node.shared.Volume.VolumeUseStatus;

public class VolumeEditViewImpl extends ViewImpl implements VolumeEditView {
    private final Widget widget;
    @UiField
    ItemListBox<Node> node;
    @UiField
    TextBox path;
    @UiField
    ItemListBox<VolumeType> volumeType;
    @UiField
    ItemListBox<VolumeUseStatus> streamStatus;
    @UiField
    ItemListBox<VolumeUseStatus> indexStatus;
    @UiField
    TextBox bytesLimit;
    @Inject
    public VolumeEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public ItemListBox<Node> getNode() {
        return node;
    }

    @Override
    public HasText getPath() {
        return path;
    }

    @Override
    public ItemListBox<VolumeType> getVolumeType() {
        return volumeType;
    }

    @Override
    public ItemListBox<VolumeUseStatus> getStreamStatus() {
        return streamStatus;
    }

    @Override
    public ItemListBox<VolumeUseStatus> getIndexStatus() {
        return indexStatus;
    }

    @Override
    public HasText getBytesLimit() {
        return bytesLimit;
    }

    public interface Binder extends UiBinder<Widget, VolumeEditViewImpl> {
    }
}

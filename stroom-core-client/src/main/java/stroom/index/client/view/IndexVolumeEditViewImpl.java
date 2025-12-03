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

package stroom.index.client.view;

import stroom.index.client.presenter.IndexVolumeEditPresenter.IndexVolumeEditView;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.item.client.SelectionBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.List;

public class IndexVolumeEditViewImpl extends ViewImpl implements IndexVolumeEditView {

    private final Widget widget;

    @UiField
    SelectionBox<String> nodeName;
    @UiField
    TextBox path;
    @UiField
    SelectionBox<VolumeUseState> state;
    @UiField
    TextBox bytesLimit;

    @Inject
    public IndexVolumeEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        nodeName.focus();
    }

    @Override
    public void setNodeNames(final List<String> nodeNames) {
        this.nodeName.clear();
        this.nodeName.addItems(nodeNames);
    }

    @Override
    public HasText getNodeName() {
        return new HasText() {
            @Override
            public String getText() {
                return nodeName.getValue();
            }

            @Override
            public void setText(final String text) {
                nodeName.setValue(text);
            }
        };
    }

    @Override
    public HasText getPath() {
        return path;
    }

    @Override
    public SelectionBox<VolumeUseState> getState() {
        return state;
    }

    @Override
    public HasText getByteLimit() {
        return bytesLimit;
    }

    public interface Binder extends UiBinder<Widget, IndexVolumeEditViewImpl> {

    }
}

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

package stroom.streamstore.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.item.client.ItemListBox;
import stroom.streamstore.client.presenter.StreamFilterPresenter.StreamFilterView;
import stroom.streamstore.client.presenter.StreamListFilterTemplate;
import stroom.streamstore.shared.StreamStatus;
import stroom.util.shared.HasLongValue;
import stroom.widget.customdatebox.client.CustomDateBox;

public class StreamFilterViewImpl extends ViewImpl implements StreamFilterView {
    public interface Binder extends UiBinder<Widget, StreamFilterViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    ItemListBox<StreamListFilterTemplate> streamListFilterTemplate;

    @UiField
    DockLayoutPanel layout;
    @UiField
    SimplePanel folder;
    @UiField
    SimplePanel feed;
    @UiField
    SimplePanel pipeline;
    @UiField
    SimplePanel streamType;
    @UiField
    SimplePanel streamAttributes;
    @UiField
    CustomDateBox createFrom;
    @UiField
    CustomDateBox createTo;
    @UiField
    CustomDateBox effectiveFrom;
    @UiField
    CustomDateBox effectiveTo;
    @UiField
    TextBox streamId;
    @UiField
    TextBox parentStreamId;
    @UiField
    Widget southPanel;
    @UiField
    Widget folderPanel;
    @UiField
    Widget feedPanel;
    @UiField
    Widget pipelinePanel;
    @UiField
    Widget streamAttributesPanel;
    @UiField
    FlowPanel advancedPanel;
    @UiField(provided = true)
    ItemListBox<StreamStatus> streamStatus;
    @UiField
    CustomDateBox statusFrom;
    @UiField
    CustomDateBox statusTo;

    @Inject
    public StreamFilterViewImpl(final Binder binder) {
        streamListFilterTemplate = new ItemListBox<StreamListFilterTemplate>("");
        streamListFilterTemplate.addItems(StreamListFilterTemplate.values());
        streamStatus = new ItemListBox<>("Any");

        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HasLongValue getEffectiveFrom() {
        return effectiveFrom;
    }

    @Override
    public HasLongValue getEffectiveTo() {
        return effectiveTo;
    }

    @Override
    public HasLongValue getCreateFrom() {
        return createFrom;
    }

    @Override
    public HasLongValue getCreateTo() {
        return createTo;
    }

    @Override
    public HasText getStreamId() {
        return streamId;
    }

    @Override
    public HasText getParentStreamId() {
        return parentStreamId;
    }

    @Override
    public void setFolderView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        this.folder.setWidget(w);
    }

    @Override
    public void setFeedView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        this.feed.setWidget(w);
    }

    @Override
    public void setPipelineView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        this.pipeline.setWidget(w);
    }

    @Override
    public void setStreamTypeView(final View view) {
        final Widget w = view.asWidget();
        w.setSize("100%", "100%");
        this.streamType.setWidget(w);
    }

    @Override
    public void setStreamAttributeListView(final View view) {
        streamAttributes.setWidget(view.asWidget());
    }

    @Override
    public ItemListBox<StreamListFilterTemplate> getStreamListFilterTemplate() {
        return streamListFilterTemplate;
    }

    @Override
    public void setFolderVisible(final boolean visible) {
        folderPanel.setVisible(visible);
    }

    @Override
    public void setFeedVisible(final boolean visible) {
        feedPanel.setVisible(visible);
    }

    @Override
    public void setPipelineVisible(final boolean visible) {
        pipelinePanel.setVisible(visible);

    }

    @Override
    public void setStreamAttributeListVisible(final boolean visible) {
        streamAttributesPanel.setVisible(visible);
    }

    @Override
    public boolean isAdvancedVisible() {
        return advancedPanel.isVisible();
    }

    @Override
    public void setAdvancedVisible(final boolean visible) {
        advancedPanel.setVisible(visible);
        if (visible) {
            layout.setWidgetSize(southPanel, 270);
        } else {
            layout.setWidgetSize(southPanel, 193);
        }
    }

    @Override
    public HasLongValue getStatusFrom() {
        return statusFrom;
    }

    @Override
    public HasLongValue getStatusTo() {
        return statusTo;
    }

    @Override
    public ItemListBox<StreamStatus> getStreamStatus() {
        return streamStatus;
    }
}

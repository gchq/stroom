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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticProcessingPresenter.AnalyticProcessingView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.StringListBox;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AnalyticProcessingViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AnalyticProcessingView {

    private final Widget widget;

    @UiField
    CustomCheckBox enabled;
    @UiField
    SimplePanel expression;
    @UiField
    MyDateBox minMetaCreateTimeMs;
    @UiField
    MyDateBox maxMetaCreateTimeMs;
    @UiField
    StringListBox node;
    @UiField
    SimplePanel info;

    private String selectedNode;

    @Inject
    public AnalyticProcessingViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
    }

    @Override
    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMinMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
        this.minMetaCreateTimeMs.setMilliseconds(minMetaCreateTimeMs);
    }

    @Override
    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs.getMilliseconds();
    }

    @Override
    public void setMaxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
        this.maxMetaCreateTimeMs.setMilliseconds(maxMetaCreateTimeMs);
    }

    @Override
    public void setNodes(final List<String> nodes) {
        this.node.clear();
        this.node.addItems(nodes);
        if (selectedNode == null) {
            if (nodes.size() > 0) {
                this.node.setSelected(nodes.get(0));
            }
        } else {
            this.node.setSelected(selectedNode);
            selectedNode = null;
        }
    }

    @Override
    public String getNode() {
        return this.node.getSelected();
    }

    @Override
    public void setNode(final String node) {
        if (node != null) {
            selectedNode = node;
            this.node.setSelected(node);
        }
    }

    @Override
    public void setInfo(final SafeHtml info) {
        this.info.setWidget(new HTML(info));
    }

    @UiHandler("enabled")
    public void onEnabled(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("minMetaCreateTimeMs")
    public void onMinMetaCreateTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("maxMetaCreateTimeMs")
    public void onMaxMetaCreateTimeMs(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("node")
    public void onNode(final ValueChangeEvent<String> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, AnalyticProcessingViewImpl> {

    }
}

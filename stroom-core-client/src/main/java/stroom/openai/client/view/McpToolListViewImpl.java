/*
 * Copyright 2026 Crown Copyright
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

package stroom.openai.client.view;

import stroom.openai.client.presenter.McpToolListPresenter.McpToolListView;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.dropdowntree.client.view.QuickFilter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/// A server's tools: a tool strip with a quick filter, the tools, and below them the selected tool's
/// documentation.
public class McpToolListViewImpl extends ViewImpl implements McpToolListView {

    private final Widget widget;
    @UiField
    ButtonPanel buttons;
    @UiField
    QuickFilter filter;
    @UiField
    Label status;
    @UiField
    SimplePanel grid;
    @UiField
    HTML details;

    @Inject
    public McpToolListViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public ButtonPanel getButtons() {
        return buttons;
    }

    @Override
    public QuickFilter getFilter() {
        return filter;
    }

    @Override
    public void setGrid(final Widget widget) {
        grid.setWidget(widget);
    }

    @Override
    public void setStatus(final String text) {
        status.setText(text);
    }

    @Override
    public void setDetails(final SafeHtml html) {
        details.setHTML(html);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, McpToolListViewImpl> {

    }
}

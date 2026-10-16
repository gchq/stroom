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

import stroom.openai.client.presenter.OpenAIModelMcpToolServersPresenter;
import stroom.openai.client.presenter.OpenAIModelMcpToolServersPresenter.McpToolServersView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

/// The MCP Tool Servers tab: the servers, and below a divider, the tools of the one selected.
public class McpToolServersViewImpl extends ViewImpl implements McpToolServersView {

    private final Widget widget;
    @UiField
    SimplePanel servers;
    @UiField
    SimplePanel tools;

    @Inject
    public McpToolServersViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (OpenAIModelMcpToolServersPresenter.SERVERS.equals(slot)) {
            servers.setWidget(content);
        } else if (OpenAIModelMcpToolServersPresenter.TOOLS.equals(slot)) {
            tools.setWidget(content);
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, McpToolServersViewImpl> {

    }
}

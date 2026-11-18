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

package stroom.ai.client;

import stroom.ai.client.AiToolAccessPresenter.AiToolAccessView;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/// Holds the effective MCP tool access panel, filling the space it is given.
public class AiToolAccessViewImpl extends ViewImpl implements AiToolAccessView {

    private final SimplePanel container = new SimplePanel();

    /// Makes the view, empty.
    public AiToolAccessViewImpl() {
        container.setStyleName("max");
    }

    @Override
    public void setPanel(final IsWidget panel) {
        container.setWidget(panel);
    }

    @Override
    public Widget asWidget() {
        return container;
    }
}

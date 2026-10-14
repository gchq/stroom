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

import stroom.ai.client.AskStroomAiTabPresenter.AskStroomAiTabView;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

/**
 * Holds the chat in a content tab.
 */
public class AskStroomAiTabViewImpl extends ViewImpl implements AskStroomAiTabView {

    private final SimplePanel panel;

    public AskStroomAiTabViewImpl() {
        panel = new SimplePanel();
        panel.setStyleName("max askStroomAiTab");
    }

    @Override
    public Widget asWidget() {
        return panel;
    }

    @Override
    public void setContent(final Widget content) {
        panel.setWidget(content);
    }
}

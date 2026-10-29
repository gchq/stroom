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

package stroom.ai.client.component;

import stroom.explorer.client.component.FindPanel;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Chat History's content: a filter ([FindPanel]) above the user's Ask Stroom AI chats
/// ([AiChatHistoryResultList]).
public class AiChatHistory extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FindPanel findPanel;
    @UiField
    AiChatHistoryResultList resultList;

    /// Makes the content, with no chats.
    public AiChatHistory() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The filter and what it finds.
    public FindPanel getFindPanel() {
        return findPanel;
    }

    /// @return The chats, which its controller attaches the list's controller to.
    public AiChatHistoryResultList getResultList() {
        return resultList;
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, AiChatHistory> {

    }
}

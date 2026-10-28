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

package stroom.gwt.workbench.client.widgets;

import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

/// A controller whose widget is a given one, for showing story content in Stroom's popups and
/// dialogs (`ShowPopupEvent`) and tabs. It stands in for the controller of whatever screen a Stroom
/// dialog or tab would hold.
public final class StoryContent extends Controller {

    /// Creates a controller showing a widget.
    ///
    /// @param eventBus The event bus that the popup will be shown with.
    /// @param content  The widget to show.
    public StoryContent(final EventBus eventBus, final Widget content) {
        super(eventBus);
        initWidget(content);
    }

    /// Creates a controller showing HTML, e.g. some paragraphs.
    ///
    /// @param eventBus The event bus that the popup will be shown with.
    /// @param html     The (trusted, constant) HTML to show.
    /// @return The controller.
    public static StoryContent html(final EventBus eventBus, final String html) {
        return new StoryContent(eventBus, new HTML(html));
    }
}

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

package stroom.widget.layout.client;

import com.google.gwt.user.client.ui.ScrollPanel;

/// A scroll panel that fills its parent, its content at least as wide and tall as it
/// (`scroll-pane.css`), e.g. a tree or a list that scrolls in a dialog:
///
/// ```
/// <l:ScrollPane addStyleNames="dock-max form-control-border">
///     <g:SimplePanel ui:field="tree"/>
/// </l:ScrollPane>
/// ```
///
/// Give it more classes with `addStyleNames` (not `styleName`, which would replace its own).
public class ScrollPane extends ScrollPanel {

    private static final String BLOCK = "scroll-pane";

    /// Makes an empty pane.
    public ScrollPane() {
        addStyleName(BLOCK);
        getContainerElement().addClassName(BLOCK + "__content");
    }
}

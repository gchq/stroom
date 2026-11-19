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

package stroom.widget.button.client;

import com.google.gwt.user.client.ui.FlowPanel;

/// A row of [Toolbar]s spread across the space it has: the first at the start, the last at the
/// end. Each toolbar is its own tab stop, so a row's actions and, say, its paging buttons are
/// reached separately.
public class ToolbarRow extends FlowPanel {

    public ToolbarRow() {
        setStyleName("toolbar-row");
    }
}

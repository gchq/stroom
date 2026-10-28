/*
 * Copyright 2023 Crown Copyright
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

package stroom.entity.client;

import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.List;

/// Tab content (e.g. an editor in a document's tab) that adds buttons to the toolbar row above it.
///
/// Buttons that sit beside the document's own (Save and Save As) join its toolbar, so that the
/// buttons together are one group and one tab stop; other toolbars or controls, apart from them on
/// the row (e.g. a query's time range and Execute button, at its end), are each their own.
public interface HasToolbar {

    /// @return Groups of buttons (e.g. a [stroom.widget.button.client.Toolbar]) to add to the
    /// document's toolbar, after its own buttons.
    default List<Widget> getDocumentButtons() {
        return Collections.emptyList();
    }

    /// @return Other toolbars or controls for the row, after the document's toolbar.
    default List<Widget> getToolbars() {
        return Collections.emptyList();
    }
}

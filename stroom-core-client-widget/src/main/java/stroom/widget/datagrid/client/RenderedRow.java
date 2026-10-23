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

package stroom.widget.datagrid.client;

import com.google.gwt.dom.client.Element;

/// A row [DataGrid] has rendered, with what it was last rendered from, so the row's DOM is
/// only written again when something it shows has changed.
final class RenderedRow {

    /// The row's element.
    final Element element;
    /// The row value it was rendered from.
    Object value;
    /// The cells' HTML it was last written with, or `null` to force the next write.
    String html;
    /// The class names it was last given.
    String className;
    /// Its page-relative index, or `-1`.
    int index = -1;
    boolean selected;
    boolean keyboard;
    boolean even;

    /// @param element The row's element.
    RenderedRow(final Element element) {
        this.element = element;
    }
}

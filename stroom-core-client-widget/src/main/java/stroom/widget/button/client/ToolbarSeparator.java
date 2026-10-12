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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

/// A line between a [Toolbar]'s buttons. The buttons either side are still one group, so the arrow
/// keys cross it. Made with [Toolbar#separator()], or declared in a template as
/// `<b:ToolbarSeparator/>`.
public class ToolbarSeparator extends Widget {

    public ToolbarSeparator() {
        final Element element = Document.get().createDivElement();
        element.setClassName("toolbar__separator");
        element.setAttribute("role", "separator");
        element.setAttribute("aria-orientation", "vertical");
        setElement(element);
    }
}

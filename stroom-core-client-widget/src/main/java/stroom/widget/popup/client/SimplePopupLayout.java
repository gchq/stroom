/*
 * Copyright 2016 Crown Copyright
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

package stroom.widget.popup.client;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The look of a popup (the BEM block `popup`) for a popup panel that isn't a [SimplePopup]
/// (e.g. a selection box's list): one element, holding the content.
public class SimplePopupLayout extends SimplePanel {

    /// Creates an empty layout. Its content is set with [#setContent(Widget)].
    public SimplePopupLayout() {
        setStyleName("popup");
    }

    /// @param widget The popup's content.
    public void setContent(final Widget widget) {
        setWidget(widget);
    }
}

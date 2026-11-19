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

package stroom.content.client.component;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Focus;

/// The curved tab layout's root (`CurveTabLayout`): its tab bar and its content. It can be
/// given the focus as a whole (the main view focuses its centre so), which goes where it is told.
public class CurveTabLayoutPanel extends FlowPanel implements Focus {

    private Runnable onFocus = () -> {
    };

    /// @param onFocus Puts the focus where the layout takes it, e.g. on its first button.
    public void setOnFocus(final Runnable onFocus) {
        this.onFocus = onFocus;
    }

    @Override
    public void focus() {
        onFocus.run();
    }
}

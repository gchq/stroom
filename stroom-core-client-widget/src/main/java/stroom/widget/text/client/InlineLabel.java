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

package stroom.widget.text.client;

/// Text within a line (a `span`), Stroom's version of GWT's `InlineLabel`: its class is
/// `inline-label`, not GWT's `gwt-InlineLabel`. It has no styles of its own.
public class InlineLabel extends com.google.gwt.user.client.ui.InlineLabel {

    private static final String BLOCK = "inline-label";

    /// Makes empty text.
    public InlineLabel() {
        setStyleName(BLOCK);
    }

    /// @param text The text.
    public InlineLabel(final String text) {
        this();
        setText(text);
    }
}

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

/// A line of text (a `div`), Stroom's version of GWT's `Label`: its class is `text-label`
/// (`text-label.css`, which keeps it on one line), not GWT's `gwt-Label`. As with GWT's, a template's
/// `styleName` replaces the class (and so lets the text wrap), and `addStyleNames` adds to it.
public class Label extends com.google.gwt.user.client.ui.Label {

    private static final String BLOCK = "text-label";

    /// Makes an empty label.
    public Label() {
        setStyleName(BLOCK);
    }

    /// @param text The label's text.
    public Label(final String text) {
        this();
        setText(text);
    }

    /// @param text     The label's text.
    /// @param wordWrap Whether the text may wrap.
    public Label(final String text, final boolean wordWrap) {
        this(text);
        setWordWrap(wordWrap);
    }
}

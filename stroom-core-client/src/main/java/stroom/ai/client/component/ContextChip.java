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

package stroom.ai.client.component;

import stroom.svg.shared.SvgImage;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

/// The markup of the chip above the chat's message box that says what data is attached to the next
/// message: its icon, then its description as text (a table's or dashboard's name, which may hold
/// anything, so it is escaped).
public final class ContextChip {

    private ContextChip() {
        // Static utility
    }

    /// @param icon        The chip's icon.
    /// @param description What is attached, e.g. a table's name.
    /// @return The chip's content.
    public static SafeHtml toHtml(final SvgImage icon, final String description) {
        return new SafeHtmlBuilder()
                .appendHtmlConstant("<span class='icon-wrap'>" + icon.getSvg() + "</span> ")
                .appendEscaped(description)
                .toSafeHtml();
    }
}

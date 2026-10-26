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

package stroom.widget.contextmenu.client;

import stroom.svg.shared.SvgImage;

/// A command of the edit menu ([TextEditMenu]).
public enum TextEditCommand {
    UNDO("Undo", SvgImage.UNDO),
    REDO("Redo", SvgImage.REDO),
    CUT("Cut", SvgImage.CUT),
    COPY("Copy", SvgImage.COPY),
    PASTE("Paste", SvgImage.PASTE),
    DELETE("Delete", SvgImage.DELETE),
    SELECT_ALL("Select All", SvgImage.SELECT_ALL);

    private final String text;
    private final SvgImage icon;

    TextEditCommand(final String text, final SvgImage icon) {
        this.text = text;
        this.icon = icon;
    }

    /// @return The command's menu item text, e.g. "Select All".
    public String getText() {
        return text;
    }

    /// @return The command's menu item icon.
    public SvgImage getIcon() {
        return icon;
    }
}

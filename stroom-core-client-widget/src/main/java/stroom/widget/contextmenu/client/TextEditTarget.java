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

import stroom.widget.contextmenu.client.TextEditMenu.PasteSupport;
import stroom.widget.contextmenu.client.TextEditMenu.TextFieldState;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;

/// What the edit menu ([TextEditMenu]) acts on: a text field ([TextField]) or a code editor
/// ([CodeEditorText]), as it was when the menu was opened.
public interface TextEditTarget {

    /// @return The element the menu was opened on.
    Element getElement();

    /// @return Where a menu opened from the keyboard (which has no pointer to open at) opens below:
    /// the field, or a code editor's cursor.
    Rect getMenuAnchor();

    /// @param paste Whether Paste can read the clipboard.
    /// @param mac   Whether the user is on a Mac.
    /// @return The target's state, as when the menu was opened, for [TextEditMenu#createItems].
    TextFieldState getState(PasteSupport paste, boolean mac);

    /// Puts the focus back on the target with the selection it had when the menu was opened.
    void restore();

    /// Runs a command on the target, putting its focus and selection back first.
    ///
    /// @param command   The command.
    /// @param onRefused Run if Paste couldn't read the clipboard.
    void execute(TextEditCommand command, Runnable onRefused);
}

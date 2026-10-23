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
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;

import java.util.Locale;
import java.util.Set;

/// A text field (an `input` that takes text, or a `textarea`) that the edit menu ([TextEditMenu])
/// acts on.
///
/// The menu takes the focus when it opens, so the field's selection is noted then, and put back
/// with the focus before each command runs, the commands acting on the field itself (so that they
/// join its undo history).
public final class TextField implements TextEditTarget {

    // The input types that take free text; others (e.g. check boxes, colours, files) have no edit menu
    private static final Set<String> TEXT_INPUT_TYPES = Set.of(
            "", "text", "search", "password", "email", "url", "tel");

    private final Element element;
    private final int selectionStart;
    private final int selectionEnd;

    private TextField(final Element element) {
        this.element = element;
        this.selectionStart = getSelectionStart(element);
        this.selectionEnd = getSelectionEnd(element);
    }

    /// Finds the text field a right-click was on, noting its selection.
    ///
    /// @param target The element the right-click was on.
    /// @return The field, or null if the element isn't a text field that the edit menu is for: not
    /// an input that takes text or a text area, disabled, a read-only box that opens a list (a
    /// selection box), or Ace's input (see [CodeEditorText]).
    public static TextField find(final Element target) {
        if (target == null || !isTextField(target) || isInCodeEditor(target)) {
            return null;
        }
        return new TextField(target);
    }

    /// @param element An element.
    /// @return True if the element is an input that takes text or a text area, and not disabled, nor
    /// a read-only box that opens a list (a selection box that can't be typed in).
    public static boolean isTextField(final Element element) {
        final String tagName = element.getTagName().toLowerCase(Locale.ROOT);
        final boolean textControl = "textarea".equals(tagName)
                                    || ("input".equals(tagName)
                                        && TEXT_INPUT_TYPES.contains(getType(element)));
        if (!textControl || element.getPropertyBoolean("disabled")) {
            return false;
        }
        return !(element.getPropertyBoolean("readOnly")
                 && "combobox".equals(element.getAttribute("role")));
    }

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public Rect getMenuAnchor() {
        return new Rect(element);
    }

    @Override
    public TextFieldState getState(final PasteSupport paste, final boolean mac) {
        final String value = element.getPropertyString("value");
        return new TextFieldState(
                !element.getPropertyBoolean("readOnly"),
                "password".equals(getType(element)),
                selectionEnd > selectionStart,
                value != null && !value.isEmpty(),
                true,
                true,
                paste,
                mac);
    }

    @Override
    public void restore() {
        element.focus();
        setSelectionRange(element, selectionStart, selectionEnd);
    }

    @Override
    public void execute(final TextEditCommand command, final Runnable onRefused) {
        restore();
        switch (command) {
            case UNDO -> execCommand("undo");
            case REDO -> execCommand("redo");
            case CUT -> execCommand("cut");
            case COPY -> execCommand("copy");
            case DELETE -> execCommand("delete");
            case SELECT_ALL -> select(element);
            case PASTE -> Clipboard.readText(this::insert, onRefused);
        }
    }

    // Inserts text in place of the selection, as typing would: undoable, and firing input events
    private void insert(final String text) {
        restore();
        if (!execCommand("insertText", text)) {
            insertWithoutUndo(element, text);
        }
    }

    private static boolean isInCodeEditor(final Element element) {
        Element parent = element;
        while (parent != null) {
            if (parent.hasClassName("ace_editor")) {
                return true;
            }
            parent = parent.getParentElement();
        }
        return false;
    }

    private static String getType(final Element element) {
        final String type = element.getAttribute("type");
        return type == null
                ? ""
                : type.toLowerCase(Locale.ROOT);
    }

    private static native int getSelectionStart(final Element element) /*-{
        return element.selectionStart || 0;
    }-*/;

    private static native int getSelectionEnd(final Element element) /*-{
        return element.selectionEnd || 0;
    }-*/;

    private static native void setSelectionRange(final Element element, final int start, final int end) /*-{
        try {
            element.setSelectionRange(start, end);
        } catch (e) {
            // Some input types (e.g. email) don't have a selection
        }
    }-*/;

    private static native void select(final Element element) /*-{
        element.select();
    }-*/;

    private static native boolean execCommand(final String command) /*-{
        return $doc.execCommand(command);
    }-*/;

    private static native boolean execCommand(final String command, final String value) /*-{
        return $doc.execCommand(command, false, value);
    }-*/;

    // Where the browser can't insert text as typing does, sets the value directly (losing the undo
    // step) and says that it changed
    private static native void insertWithoutUndo(final Element element, final String text) /*-{
        element.setRangeText(text, element.selectionStart, element.selectionEnd, 'end');
        element.dispatchEvent(new Event('input', {bubbles: true}));
    }-*/;
}

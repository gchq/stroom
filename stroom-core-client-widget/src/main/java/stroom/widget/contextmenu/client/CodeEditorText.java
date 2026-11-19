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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;

/// The text of an Ace code editor, which the edit menu ([TextEditMenu]) acts on through Ace's own
/// commands, so that they join its undo history.
///
/// Ace keeps its selection when it loses the focus, so unlike a [TextField] nothing needs noting
/// when the menu opens.
public final class CodeEditorText implements TextEditTarget {

    private static final String ACE_EDITOR_CLASS = "ace_editor";

    private final Element element;
    private final JavaScriptObject editor;

    private CodeEditorText(final Element element, final JavaScriptObject editor) {
        this.element = element;
        this.editor = editor;
    }

    /// Finds the code editor a right-click was in.
    ///
    /// @param target The element the right-click was on.
    /// @return The editor's text, or null if the element isn't in an Ace editor.
    public static CodeEditorText find(final Element target) {
        Element element = target;
        while (element != null) {
            if (element.hasClassName(ACE_EDITOR_CLASS)) {
                final JavaScriptObject editor = getAceEditor(element);
                return editor != null
                        ? new CodeEditorText(element, editor)
                        : null;
            }
            element = element.getParentElement();
        }
        return null;
    }

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public Rect getMenuAnchor() {
        return new Rect(getCursorTop(editor), getCursorBottom(editor), getCursorLeft(editor),
                getCursorLeft(editor));
    }

    @Override
    public TextFieldState getState(final PasteSupport paste, final boolean mac) {
        return new TextFieldState(
                !isReadOnly(editor),
                false,
                hasSelection(editor),
                hasText(editor),
                hasUndo(editor),
                hasRedo(editor),
                paste,
                mac);
    }

    @Override
    public void restore() {
        focus(editor);
    }

    @Override
    public void execute(final TextEditCommand command, final Runnable onRefused) {
        restore();
        switch (command) {
            case UNDO -> undo(editor);
            case REDO -> redo(editor);
            case CUT -> {
                Clipboard.writeText(getCopyText(editor));
                removeSelection(editor);
                restore();
            }
            case COPY -> {
                Clipboard.writeText(getCopyText(editor));
                restore();
            }
            case DELETE -> removeSelection(editor);
            case SELECT_ALL -> selectAll(editor);
            case PASTE -> Clipboard.readText(text -> {
                restore();
                insert(editor, text);
            }, onRefused);
        }
    }

    // Ace keeps the editor on the element it was created on (ace.edit sets element.env.editor)
    private static native JavaScriptObject getAceEditor(final Element element) /*-{
        return element.env && element.env.editor
                ? element.env.editor
                : null;
    }-*/;

    // The cursor's position on the page, from Ace's renderer
    private static native double getCursorTop(final JavaScriptObject editor) /*-{
        var cursor = editor.getCursorPosition();
        return editor.renderer.textToScreenCoordinates(cursor.row, cursor.column).pageY;
    }-*/;

    private static native double getCursorBottom(final JavaScriptObject editor) /*-{
        var cursor = editor.getCursorPosition();
        return editor.renderer.textToScreenCoordinates(cursor.row, cursor.column).pageY
                + editor.renderer.lineHeight;
    }-*/;

    private static native double getCursorLeft(final JavaScriptObject editor) /*-{
        var cursor = editor.getCursorPosition();
        return editor.renderer.textToScreenCoordinates(cursor.row, cursor.column).pageX;
    }-*/;

    private static native boolean isReadOnly(final JavaScriptObject editor) /*-{
        return editor.getReadOnly();
    }-*/;

    private static native boolean hasSelection(final JavaScriptObject editor) /*-{
        return !editor.selection.isEmpty();
    }-*/;

    private static native boolean hasText(final JavaScriptObject editor) /*-{
        return editor.session.getLength() > 1 || editor.session.getLine(0).length > 0;
    }-*/;

    private static native boolean hasUndo(final JavaScriptObject editor) /*-{
        return editor.session.getUndoManager().hasUndo();
    }-*/;

    private static native boolean hasRedo(final JavaScriptObject editor) /*-{
        return editor.session.getUndoManager().hasRedo();
    }-*/;

    private static native void focus(final JavaScriptObject editor) /*-{
        editor.focus();
    }-*/;

    private static native void undo(final JavaScriptObject editor) /*-{
        editor.undo();
    }-*/;

    private static native void redo(final JavaScriptObject editor) /*-{
        editor.redo();
    }-*/;

    private static native String getCopyText(final JavaScriptObject editor) /*-{
        return editor.getCopyText();
    }-*/;

    private static native void removeSelection(final JavaScriptObject editor) /*-{
        editor.session.remove(editor.getSelectionRange());
        editor.clearSelection();
    }-*/;

    private static native void selectAll(final JavaScriptObject editor) /*-{
        editor.selectAll();
    }-*/;

    private static native void insert(final JavaScriptObject editor, final String text) /*-{
        editor.insert(text);
    }-*/;
}

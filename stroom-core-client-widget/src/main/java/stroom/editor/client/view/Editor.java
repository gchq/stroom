/*
 * Copyright 2017 Crown Copyright
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

package stroom.editor.client.view;

import stroom.ui.config.shared.AceEditorTheme;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.diff.LineDiff;
import stroom.util.shared.diff.LineReplacement;
import stroom.util.shared.diff.TextEdit;
import stroom.util.shared.diff.TextEdits;
import stroom.util.shared.diff.TextPosition;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasKeyUpHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Composite;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Editor extends Composite implements
        HasValueChangeHandlers<String>,
        HasKeyDownHandlers,
        HasKeyUpHandlers {

    private final AceEditor editor;
    private String text = "";
    private boolean textDirty;
    private int firstLineNumber = 1;
    private boolean firstLineNumberDirty;
    private int maxLines = 1;
    private boolean maxLinesDirty;
    private List<Annotation> annotations;
    private boolean annotationsDirty;
    private List<Marker> markers;
    private boolean markersDirty;
    private boolean readOnly;
    private boolean readOnlyDirty;
    private AceEditorMode mode = AceEditorMode.XML;
    private boolean modeDirty = true;
    private AceEditorTheme theme = AceEditorTheme.TOMORROW_NIGHT;
    private boolean themeDirty = true;
    private boolean showGutter = true;
    private boolean showGutterDirty;
    private boolean highlightActiveLine = true;
    private boolean highlightActiveLineDirty;
    private int gotoLine;
    private boolean gotoLineDirty;
    private Location gotoLocation;
    private boolean gotoLocationDirty;
    private Rect scrollMargin;
    private boolean scrollMarginDirty;
    private boolean useWrapMode;
    private boolean useWrapModeDirty;
    private boolean showIndentGuides;
    private boolean showIndentGuidesDirty;
    private boolean showInvisibles = false;
    private boolean showInvisiblesDirty;
    private boolean useVimBindings = false;
    private boolean useVimBindingsDirty;
    private boolean useBasicAutoCompletion = false;
    private boolean useBasicAutoCompletionDirty;
    private boolean useLiveAutoCompletion = false;
    private boolean useLiveAutoCompletionDirty;
    private boolean useSnippets = false;
    private boolean useSnippetsDirty;
    private boolean addChangeHandler;
    private boolean addedChangeHandler;
    private final List<Runnable> scrollListeners = new ArrayList<>();
    private boolean addedScrollHandler;
    private List<String> lineNumberLabels;
    private boolean lineNumberLabelsDirty;
    private boolean started;

    public Editor() {
        editor = new AceEditor();
        // Use an attach handler to set up the editor as we can't start it
        // until it is attached.
        editor.addAttachHandler(event -> {
            if (event.isAttached()) {
                if (!started) {
                    // Can only be started once attached
                    editor.startEditor(theme.getName());

                    // TODO 04/01/2022 AT: This should be made configurable for dev use but can wait till v7.1
                    //  as lots of the UI code has changed between 7.0 and 7.1.
                    //  It does not work with modal dialogs as GWT's PopupPanel will intercept all mouse/key
                    //  events that are not in the dialog, thus stopping you interacting with or closing the
                    //  settings menu.
                    // For list of commands see
                    // https://github.com/ajaxorg/ace/blob/master/lib/ace/commands/default_commands.js
                    editor.removeCommandByName("showSettingsMenu");

                    editor.setShowPrintMargin(false);
                    editor.setUseSoftTabs(true);
                    editor.setTabSize(2);
                    started = true;

                    DOM.setEventListener(editor.getTextInputElement(), e -> {
                        if (Objects.equals(e.getType(), "keydown")) {
                            KeyDownEvent.fireNativeEvent(e, Editor.this, editor.getTextInputElement());
                        } else if (Objects.equals(e.getType(), "keyup")) {
                            KeyUpEvent.fireNativeEvent(e, Editor.this, editor.getTextInputElement());
                        }
                    });
                }

                updateAnnotations();
                updateChangeHandler();
                updateFirstLineNumber();
                updateGotoLine();
                updateHighlightActiveLine();
                updateMarkers();
                updateMode();
                updateReadOnly();
                updateScrollMargin();
                updateShowGutter();
                updateShowGutter();
                updateShowInvisibles();
                updateText();
                updateTheme();
                updateUseBasicAutoCompletion();
                updateUseLiveAutoCompletion();
                updateUseSnippets();
                updateUseVimBindings();
                updateUseWrapMode();
                updateShowIndentGuides();
                updateScrollHandler();
                updateLineNumberLabels();
            }
        });

        editor.getElement().setClassName("editor");
        initWidget(editor);
    }

    public String getId() {
        return editor.getId();
    }

    public String getText() {
        if (started) {
            return editor.getText();
        }

        return text;
    }

    public void setText(final String text) {
        textDirty = true;
        if (text == null) {
            this.text = "";
        } else {
            this.text = text;
        }
        updateText();

        gotoLineDirty = false;
    }

    public void insertTextAtCursor(final String text) {
        if (started) {
            editor.insertAtCursor(text);
            this.text = editor.getText();
        }
    }

    public void replaceSelectedText(final String text) {
        if (started) {
            editor.replaceSelectedText(text);
            this.text = editor.getText();
        }
    }

    public void insertSnippet(final String snippet) {
        if (started) {
            editor.insertSnippet(snippet);
            this.text = editor.getText();
        }
    }

    /// Replaces ranges of whole lines with new lines, keeping the editor's undo history, so the user
    /// can undo the change with Ctrl+Z (Ace may record one undo step per range).
    ///
    /// The text is not set as a whole, which would reset the undo history. Instead, working from the
    /// last range to the first so earlier line numbers stay valid, each range is selected and the
    /// selection replaced. The user's cursor and selection are then put back, moved to allow for any
    /// lines added or removed above them, and the scroll position is kept.
    ///
    /// @param replacements The line replacements, with line numbers relative to the current text.
    /// @return False if the editor has not started yet (it isn't attached), so nothing was changed.
    /// @throws IllegalArgumentException If a replacement is outside the text or two of them overlap.
    public boolean applyLineReplacements(final List<LineReplacement> replacements) {
        if (!started) {
            return false;
        }
        if (replacements == null || replacements.isEmpty()) {
            return true;
        }

        final List<String> lines = LineDiff.splitLines(editor.getText());
        final List<TextEdit> edits = TextEdits.fromLineReplacements(lines, replacements);

        final AceSelection selection = editor.getSelection();
        final AceEditorCursorPosition anchor = selection.getSelectionAnchor();
        final AceEditorCursorPosition lead = selection.getSelectionLead();
        final int scrollTop = editor.getScrollTop();

        for (int i = edits.size() - 1; i >= 0; i--) {
            final TextEdit edit = edits.get(i);
            selection.select(
                    edit.getFrom().getRow(),
                    edit.getFrom().getColumn(),
                    edit.getTo().getRow(),
                    edit.getTo().getColumn());
            editor.replaceSelectedText(edit.getText());
        }
        this.text = editor.getText();

        final TextPosition newAnchor = TextEdits.adjustPosition(
                lines, edits, new TextPosition(anchor.getRow(), anchor.getColumn()));
        final TextPosition newLead = TextEdits.adjustPosition(
                lines, edits, new TextPosition(lead.getRow(), lead.getColumn()));
        selection.select(newAnchor.getRow(), newAnchor.getColumn(), newLead.getRow(), newLead.getColumn());
        editor.setScrollTop(scrollTop);
        return true;
    }

    /// Adds a listener that is called whenever the editor's vertical scroll position changes, whether
    /// by the user or by [#setScrollTop(int)].
    ///
    /// @param listener The listener.
    public void addScrollListener(final Runnable listener) {
        scrollListeners.add(listener);
        updateScrollHandler();
    }

    private void updateScrollHandler() {
        if (started && !addedScrollHandler && !scrollListeners.isEmpty()) {
            final JavaScriptObject aceEditor = getAceEditor(editor.getElement());
            if (aceEditor != null) {
                addedScrollHandler = true;
                addNativeScrollHandler(aceEditor, this::fireScrolled);
            }
        }
    }

    private void fireScrolled() {
        for (final Runnable listener : scrollListeners) {
            listener.run();
        }
    }

    /// Shows the given labels in the gutter in place of line numbers, one per row, e.g. to number only
    /// some rows when blank filler rows have been added to line up a side by side diff.
    ///
    /// @param labels The label for each row (rows beyond the list have none), or null to go back to
    ///               showing line numbers.
    public void setLineNumberLabels(final List<String> labels) {
        this.lineNumberLabels = labels;
        this.lineNumberLabelsDirty = true;
        updateLineNumberLabels();
    }

    private void updateLineNumberLabels() {
        if (started && lineNumberLabelsDirty) {
            final JavaScriptObject aceEditor = getAceEditor(editor.getElement());
            if (aceEditor != null) {
                if (lineNumberLabels == null) {
                    setNativeGutterLabels(aceEditor, null, 0);
                } else {
                    final JsArrayString array = JavaScriptObject.createArray().cast();
                    int maxLength = 1;
                    for (final String label : lineNumberLabels) {
                        final String safeLabel = label == null
                                ? ""
                                : label;
                        array.push(safeLabel);
                        maxLength = Math.max(maxLength, safeLabel.length());
                    }
                    setNativeGutterLabels(aceEditor, array, maxLength);
                }
                lineNumberLabelsDirty = false;
            }
        }
    }

    // Ace keeps a reference to the editor on its container element (see ace.edit()), which lets this
    // class reach the editor without changing the AceEditor wrapper.
    private static native JavaScriptObject getAceEditor(Element element) /*-{
        if (element && element.env && element.env.editor) {
            return element.env.editor;
        }
        return null;
    }-*/;

    private static native void addNativeScrollHandler(JavaScriptObject aceEditor, Runnable runnable) /*-{
        aceEditor.getSession().on("changeScrollTop", $entry(function () {
            runnable.@java.lang.Runnable::run()();
        }));
    }-*/;

    private static native void setNativeGutterLabels(JavaScriptObject aceEditor,
                                                     JsArrayString labels,
                                                     int maxLength) /*-{
        var session = aceEditor.getSession();
        if (labels) {
            session.gutterRenderer = {
                getWidth: function (session, lastLineNumber, config) {
                    return maxLength * config.characterWidth;
                },
                getText: function (session, row) {
                    return row < labels.length ? labels[row] : "";
                }
            };
        } else {
            session.gutterRenderer = null;
        }
        aceEditor.renderer.updateFull();
    }-*/;

    private void updateText() {
        if (started && textDirty) {
            editor.setText(this.text);
            textDirty = false;
            markClean();
        }
    }

    public void setFirstLineNumber(final int firstLineNumber) {
        firstLineNumberDirty = true;
        this.firstLineNumber = firstLineNumber;
        updateFirstLineNumber();
    }

    private void updateFirstLineNumber() {
        if (started && firstLineNumberDirty) {
            editor.setFirstLineNumber(firstLineNumber);
            firstLineNumberDirty = false;
        }
    }

    public void setMaxLines(final int maxLines) {
        maxLinesDirty = true;
        this.maxLines = maxLines;
        updateMaxLines();
    }

    private void updateMaxLines() {
        if (started && maxLinesDirty) {
            editor.setMaxLines(maxLines);
            maxLinesDirty = false;
        }
    }

    public void setAnnotations(final List<Annotation> annotations) {
        annotationsDirty = true;
        this.annotations = annotations;
        updateAnnotations();
    }

    private void updateAnnotations() {
        if (started && annotationsDirty) {
            editor.clearAnnotations();
            if (annotations != null) {
                for (final Annotation annotation : annotations) {
                    editor.addAnnotation(
                            annotation.getRow(),
                            annotation.getColumn(),
                            annotation.getText(),
                            annotation.getType());
                }
                editor.setAnnotations();
            }
            annotationsDirty = false;
        }
    }

    public void setMarkers(final List<Marker> markers) {
        markersDirty = true;
        this.markers = markers;
        updateMarkers();
    }

    private void updateMarkers() {
        if (started && markersDirty) {
            editor.removeAllMarkers();
            if (markers != null) {
                for (final Marker marker : markers) {
                    editor.addMarker(marker.getRange(), marker.getClazz(), marker.getType(), marker.isInFront());
                }
            }
            markersDirty = false;
        }
    }

    public void setReadOnly(final boolean readOnly) {
        readOnlyDirty = true;
        this.readOnly = readOnly;
        updateReadOnly();
    }

    private void updateReadOnly() {
        if (started && readOnlyDirty) {
            editor.setReadOnly(readOnly);
            readOnlyDirty = false;
        }
    }

    public void setMode(final AceEditorMode mode) {
        modeDirty = true;
        this.mode = mode;
        updateMode();
    }

    private void updateMode() {
        if (started && modeDirty) {
            if (mode != null) {
                try {
                    editor.setMode(mode);


//                    GWT.log("mode: " + editor.getMode());
//                    GWT.log("mode: " + editor.getModeShortName());
                } catch (final RuntimeException e) {
                    throw new RuntimeException(
                            "Unable to set mode '" +
                                    mode.getName() +
                                    "' perhaps the javascript for this mode is missing");
                }
            }
            modeDirty = false;
        }
    }

    public void setTheme(final AceEditorTheme theme) {
        themeDirty = true;
        this.theme = theme;
        updateTheme();
    }

    private void updateTheme() {
        if (started && themeDirty) {
            if (theme != null) {
                try {
                    editor.setTheme(theme);
                } catch (final RuntimeException e) {
                    throw new RuntimeException("Unable to set theme '" +
                            theme.getName() +
                            "' perhaps the javascript for this theme is missing");
                }
            }
            themeDirty = false;
        }
    }

    public void setShowGutter(final boolean showGutter) {
        showGutterDirty = true;
        this.showGutter = showGutter;
        updateShowGutter();
    }

    private void updateShowGutter() {
        if (started && showGutterDirty) {
            editor.setShowGutter(showGutter);
            showGutterDirty = false;
        }
    }

    public void gotoLine(final int line) {
        gotoLineDirty = true;
        this.gotoLine = line;
        updateGotoLine();
    }

    private void updateGotoLine() {
        if (started && gotoLineDirty) {
            editor.gotoLine(gotoLine);
            gotoLineDirty = false;
        }
    }

    /**
     * @param lineNo One based
     * @param colNo  One based
     */
    public void gotoLocation(final int lineNo, final int colNo) {
        gotoLocation(DefaultLocation.of(lineNo, colNo));
    }

    public void gotoLocation(final Location location) {
        gotoLocationDirty = true;
        this.gotoLocation = location;
        updateGotoLocation();
    }

    private void updateGotoLocation() {
        if (started && gotoLocationDirty) {
//            GWT.log("Goto " + gotoLocation.getLineNo() + ":" + gotoLocation.getColNo());
            editor.gotoPosition(gotoLocation.getLineNo(), gotoLocation.getColNo());
            gotoLocationDirty = false;
        }
    }

    public int getLineCount() {
        if (started) {
            return editor.getLineCount();
        }
        return 0;
    }

    public void setScrollMargin(final int top, final int bottom, final int left, final int right) {
        this.scrollMarginDirty = true;
        scrollMargin = new Rect(top, bottom, left, right);
        updateScrollMargin();
    }

    private void updateScrollMargin() {
        if (editor.isAttached() && scrollMarginDirty) {
            editor.setScrollMargin(
                    scrollMargin.getTop(),
                    scrollMargin.getBottom(),
                    scrollMargin.getLeft(),
                    scrollMargin.getRight());
            scrollMarginDirty = false;
        }
    }

    public void setUseWrapMode(final boolean useWrapMode) {
        useWrapModeDirty = true;
        this.useWrapMode = useWrapMode;
        updateUseWrapMode();
    }

    private void updateUseWrapMode() {
        if (editor.isAttached() && useWrapModeDirty) {
            editor.setUseWrapMode(useWrapMode);
            useWrapModeDirty = false;
        }
    }

    public void setShowIndentGuides(final boolean showIndentGuides) {
        showIndentGuidesDirty = true;
        this.showIndentGuides = showIndentGuides;
        updateShowIndentGuides();
    }

    private void updateShowIndentGuides() {
        if (editor.isAttached() && showIndentGuidesDirty) {
            editor.setShowIndentGuides(showIndentGuides);
            showIndentGuidesDirty = false;
        }
    }

    public void setShowInvisibles(final boolean showInvisibles) {
        showInvisiblesDirty = true;
        this.showInvisibles = showInvisibles;
        updateShowInvisibles();
    }

    private void updateShowInvisibles() {
        if (editor.isAttached() && showInvisiblesDirty) {
            editor.setShowInvisibles(showInvisibles);
            showInvisiblesDirty = false;
        }
    }

    public void setUseVimBindings(final boolean useVimBindings) {
        useVimBindingsDirty = true;
        this.useVimBindings = useVimBindings;
        updateUseVimBindings();
    }

    private void updateUseVimBindings() {
        if (editor.isAttached() && useVimBindingsDirty) {
            editor.setUseVimBindings(useVimBindings);
            useVimBindingsDirty = false;
        }
    }

    public void setUseBasicAutoCompletion(final boolean useBasicAutoCompletion) {
        useBasicAutoCompletionDirty = true;
        this.useBasicAutoCompletion = useBasicAutoCompletion;
        updateUseBasicAutoCompletion();
    }

    private void updateUseBasicAutoCompletion() {
        if (editor.isAttached() && useBasicAutoCompletionDirty) {
            editor.setBasicAutoCompleteEnabled(useBasicAutoCompletion);
            useBasicAutoCompletionDirty = false;
        }
    }

    public void setUseLiveAutoCompletion(final boolean useLiveAutoCompletion) {
        useLiveAutoCompletionDirty = true;
        this.useLiveAutoCompletion = useLiveAutoCompletion;
        updateUseLiveAutoCompletion();
    }

    private void updateUseLiveAutoCompletion() {
        if (editor.isAttached() && useLiveAutoCompletionDirty) {
            editor.setLiveAutoCompleteEnabled(useLiveAutoCompletion);
            useLiveAutoCompletionDirty = false;
        }
    }

    public void setUseSnippets(final boolean useSnippets) {
        useSnippetsDirty = true;
        this.useSnippets = useSnippets;
        updateUseSnippets();
    }

    private void updateUseSnippets() {
        if (editor.isAttached() && useSnippetsDirty) {
            editor.setSnippetsEnabled(useSnippets);
            useSnippetsDirty = false;
        }
    }

    public void setHighlightActiveLine(final boolean highlightActiveLine) {
        this.highlightActiveLineDirty = true;
        this.highlightActiveLine = highlightActiveLine;
        updateHighlightActiveLine();
    }

    private void updateHighlightActiveLine() {
        if (editor.isAttached() && highlightActiveLineDirty) {
            editor.setHighlightActiveLine(highlightActiveLine);
            highlightActiveLineDirty = false;
        }
    }

    public void onResize() {
        Scheduler.get().scheduleDeferred(() -> {
            if (editor.isAttached()) {
                editor.onResize();
            }
        });
    }

    public void beautify() {
        if (editor.isAttached()) {
            editor.beautify();
        }
    }

    public int getScrollTop() {
        if (editor.isAttached()) {
            return editor.getScrollTop();
        }
        return -1;
    }

    public void setScrollTop(final int scrollTop) {
        if (editor.isAttached()) {
            editor.setScrollTop(scrollTop);
        }
    }

    public AceEditorCursorPosition getCursorPosition() {
        if (editor.isAttached()) {
            return editor.getCursorPosition();
        }
        return null;
    }

    public void moveTo(final int row, final int col) {
        if (editor.isAttached()) {
            editor.moveTo(row, col);
        }
    }

    public void focus() {
        if (editor.isAttached()) {
            Scheduler.get().scheduleDeferred(editor::focus);
        }
    }

    public boolean isClean() {
        if (editor.isAttached()) {
            return editor.isClean();
        } else {
            return true;
        }
    }

    public void markClean() {
        editor.markClean();
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        addChangeHandler = true;
        updateChangeHandler();
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return addHandler(handler, KeyDownEvent.getType());
    }

    @Override
    public HandlerRegistration addKeyUpHandler(final KeyUpHandler handler) {
        return addHandler(handler, KeyUpEvent.getType());
    }

    private void updateChangeHandler() {
        if (editor.isAttached() && addChangeHandler && !addedChangeHandler) {
            addedChangeHandler = true;
            editor.addOnChangeHandler(obj -> {
                // Only fire value change events for edits, not where setText() is called.
                if (!textDirty) {
                    ValueChangeEvent.fire(Editor.this, null);
                }
            });
            addChangeHandler = false;
        }
    }
}

//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package edu.ycp.cs.dh.acegwt.client.ace;

import stroom.ui.config.shared.AceEditorTheme;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.TakesValue;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.RequiresResize;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A GWT widget for the Ajax.org Code Editor (ACE).
 *
 * @see <a href="http://ace.ajax.org/">Ajax.org Code Editor</a>
 */
public class AceEditor extends Composite implements RequiresResize, HasText, TakesValue<String> {

    // Used to generate unique element ids for Ace widgets.
    private static int nextId = 0;

    private final String elementId;

    private JavaScriptObject editor;

    private JsArray<AceAnnotation> annotations = JavaScriptObject.createArray().cast();

    private final Element divElement;

    private final HashMap<Integer, AceRange> markers = new HashMap<>();

    private AceSelection selection = null;

    private AceCommandLine commandLine = null;

    // =====================================================================
    // Added for Stroom by at055612 START
    // =====================================================================


    // To disable the local words from the completions we can do something like
    //    componentDidMount() {
    //        let langTools = ace.acequire('ace/ext/language_tools')
    //        langTools.setCompleters([langTools.snippetCompleter, langTools.keyWordCompleter])
    //    }
    // but that applies globally so we would need to maybe add a local variable enabled
    // state to this class then we could make a wrapper completer that wraps the local one
    // and checks the state first.

    /**
     * Get the editor's ID
     */
    public String getId() {
        if (editor == null) {
            throw new RuntimeException("Trying to use editor before startEditor has been called.");
        }
        return getIdNative();
    }

    /**
     * Get the editor's ID
     */
    private native String getIdNative() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
        return editor.id;
	}-*/;

    /**
     * Get the current mode
     */
    public AceEditorMode getMode() {
        final String shortModeName = getModeShortName();
        return AceEditorMode.fromName(shortModeName);
    }

    /**
     * Get the mode
     */
    public native String getModeShortName() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var shortModeName = editor.getSession()
            .$modeId
            .replace('ace\/mode\/', '');
        return shortModeName;
	}-*/;

    /**
     * Set whether to show the indent guides
     *
     * @param showIndentGuides true if vertical indent guides should be shown
     */
    public native void setShowIndentGuides(boolean showIndentGuides) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setOption("displayIndentGuides", showIndentGuides)
	}-*/;

    /**
     * Set whether to show hidden chars or not
     *
     * @param showInvisibles true if hidden chars should be displayed
     */
    public native void setShowInvisibles(boolean showInvisibles) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (showInvisibles) {
			editor.setOptions({ showInvisibles: true });
		} else {
			editor.setOptions({ showInvisibles: false });
		}
	}-*/;

    public native void setUseVimBindings(boolean useVimBindings) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (useVimBindings) {
            $wnd.ace.require("ace/keyboard/vim");
            editor.setKeyboardHandler('ace/keyboard/vim', function() {
                var vimKeyboard = $wnd.ace.require("ace/keyboard/vim");
                if (vimKeyboard) {
                    if (vimKeyboard.Vim) {
                        // TODO: 21/06/2023 Ideally we would have a user pref for additional
                        // vim mappings so users can set their own. E.g. some yaml containing
                        // a list of
                        vimKeyboard.Vim.map("jk", "<Esc>", "insert");
//                        console.log("Mapping 'jk' to '<Esc>' in 'insert' mode");
                    } else {
//                        console.log("null Vim");
                    }
                } else {
//                    console.log("null vimKeyboard");
                }
            });
        } else {
            editor.setKeyboardHandler(null);
        }
	}-*/;

    /**
     * Set or unset highlighting of current line.
     *
     * @param highlightActiveLine true to highlight current line
     */
    public native void setHighlightActiveLine(boolean highlightActiveLine) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setHighlightActiveLine(highlightActiveLine);
	}-*/;

    /**
     * Set whether or not live autocomplete is enabled.
     *
     * @param basicAutoCompleteEnabled true if basic autocomplete should be enabled, false if not
     */
    public native void setBasicAutoCompleteEnabled(boolean basicAutoCompleteEnabled) /*-{
		// See: https://github.com/ajaxorg/ace/wiki/How-to-enable-Autocomplete-in-the-Ace-editor
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (basicAutoCompleteEnabled) {
			$wnd.ace.require("ace/ext/language_tools");
		}
        editor.setOption("enableBasicAutocompletion", basicAutoCompleteEnabled)

//		console.log("enableBasicAutocompletion " + editor.getOption("enableBasicAutocompletion"))
//		console.log("enableSnippets " + editor.getOption("enableSnippets"))
//		console.log("enableLiveAutocompletion " + editor.getOption("enableLiveAutocompletion"))
	}-*/;


    /**
     * Set whether or not live autocomplete is enabled.
     *
     * @param liveAutoCompleteEnabled true if live autocomplete should be enabled, false if not
     */
    public native void setLiveAutoCompleteEnabled(boolean liveAutoCompleteEnabled) /*-{
		// See: https://github.com/ajaxorg/ace/wiki/How-to-enable-Autocomplete-in-the-Ace-editor
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (liveAutoCompleteEnabled) {
			$wnd.ace.require("ace/ext/language_tools");
		}
        editor.setOption("enableLiveAutocompletion", liveAutoCompleteEnabled)

//		console.log("enableBasicAutocompletion " + editor.getOption("enableBasicAutocompletion"))
//		console.log("enableSnippets " + editor.getOption("enableSnippets"))
//		console.log("enableLiveAutocompletion " + editor.getOption("enableLiveAutocompletion"))
	}-*/;

    /**
     * Set whether or not completion snippets are enabled.
     *
     * @param snippetsEnabled true if snippets should be enabled, false if not
     */
    public native void setSnippetsEnabled(boolean snippetsEnabled) /*-{
		// See: https://github.com/ajaxorg/ace/wiki/How-to-enable-Autocomplete-in-the-Ace-editor
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (snippetsEnabled) {
			$wnd.ace.require("ace/ext/language_tools");
		}
        editor.setOption("enableSnippets", snippetsEnabled)
//		console.log("enableBasicAutocompletion " + editor.getOption("enableBasicAutocompletion"))
//		console.log("enableSnippets " + editor.getOption("enableSnippets"))
//		console.log("enableLiveAutocompletion " + editor.getOption("enableLiveAutocompletion"))
	}-*/;

    /**
     * Replace the selected text with the passed text
     *
     * @param text text to use in place of the selected text
     */
    public native void replaceSelectedText(String text) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
        selectedContent = editor.getSelectedText();
        range = editor.selection.getRange();
        if (range.isEmpty()) {
            editor.insert(text);
        } else {
            editor.session.replace(range, text);
        }
	}-*/;

    /**
     * Insert the supplied snippet.
     *
     * @param snippet The snippet to insert e.g "concat('${1:arg1}', '${2;arg2}')$0"
     */
    public native void insertSnippet(final String snippet) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
        $wnd.ace.require("ace/ext/language_tools");

        editor.insertSnippet(snippet);
	}-*/;

    /**
     * Go to given line.
     *
     * @param line the line to go to, one based
     * @param col  the col to go to, one based
     */
    public native void gotoPosition(final int line, final int col) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;

		// Convert col to zero based
		editor.gotoLine(line, col - 1, true);
        var cursorPos = editor.getCursorPosition();

        // 0.5 means position the cursor half way up the editor
        editor.renderer.scrollCursorIntoView({
            row: cursorPos.row,
            column: cursorPos.column
            //row: line,
            //column: col - 1
        }, 0.5);
	}-*/;

    // =====================================================================
    // Added for Stroom by at055612 FINISH
    // =====================================================================

    /**
     * Preferred constructor.
     */
    public AceEditor() {
        elementId = "_aceGWT" + nextId;
//        GWT.log("Initialising editor with elementId " + elementId);

        nextId++;
        final FlowPanel div = new FlowPanel();
        div.getElement().setId(elementId);
        initWidget(div);
        divElement = div.getElement();
    }

    /**
     * Do not use this constructor: just use the default constructor.
     *
     * @param unused this parameter is ignored
     */
    @Deprecated
    public AceEditor(final boolean unused) {
        this();
    }

    /**
     * Removes all existing completers from the langtools<br><br>
     * This can be used to disable all completers including local completers, which can be very useful
     * when completers are used on very large files (as the local completer tokenizes every word to put in the selected list).<br><br>
     * <strong>NOTE:</strong> This method may be removed, and replaced with another solution. It works at point of check-in, but treat this as unstable for now.
     */
    public static native void removeAllExistingCompleters() /*-{
		var langTools = $wnd.ace.require("ace/ext/language_tools");
		langTools.setCompleters([]);
    }-*/;

    /**
     * Add an {@link AceCompletionProvider} to provide
     * custom code completions.
     * <p>
     * <strong>Warning</strong>: this is an experimental feature of AceGWT.
     * It is possible that the API will change in an incompatible way
     * in future releases.
     *
     * @param provider the {@link AceCompletionProvider}
     */
    public static native void addCompletionProvider(AceCompletionProvider provider) /*-{
		var langTools = $wnd.ace.require("ace/ext/language_tools");
		var completer = {
			getCompletions: function(editor, session, pos, prefix, callback) {
				var callbackWrapper =
					@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::wrapCompletionCallback(Lcom/google/gwt/core/client/JavaScriptObject;)(callback);
				var aceEditor = editor._aceGWTAceEditor;
				provider.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider::getProposals(Ledu/ycp/cs/dh/acegwt/client/ace/AceEditor;Ledu/ycp/cs/dh/acegwt/client/ace/AceEditorCursorPosition;Ljava/lang/String;Ledu/ycp/cs/dh/acegwt/client/ace/AceCompletionCallback;)(
					aceEditor,
					@edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition::create(II)( pos.row, pos.column ),
					prefix,
					callbackWrapper
				);
			},
		    getDocTooltip: function(item) {
		    	if ( (!item.docHTML) && item.aceGwtHtmlTooltip != null) {
		        	item.docHTML = item.aceGwtHtmlTooltip;
		    	}
		    }
		};
		langTools.addCompleter(completer);
	}-*/;

    private static AceCompletionCallback wrapCompletionCallback(final JavaScriptObject jsCallback) {

        return new AceCompletionCallbackImpl(jsCallback);
    }

    /**
     * Call this method to start the editor.
     * Make sure that the widget has been attached to the DOM tree
     * before calling this method.
     */
    public native void startEditor(final String theme) /*-{

        var element = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::divElement;
        var ace = $wnd.ace;
        ace.config.$defaultOptions.renderer.theme.initialValue = "ace/theme/" + theme;
        var editor = ace.edit(element, {
            theme: "ace/theme/" + theme,
        });

		var session = editor.getSession();
		session.setUseWorker(false);

		// Normalise line endings to `LF`, including for pasted content
		session.setNewLineMode("unix");

		this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor = editor;

		// Store a reference to the (Java) AceEditor object in the
		// JavaScript editor object.
		editor._aceGWTAceEditor = this;

//        console.log("starting editor")

		// I have been noticing sporadic failures of the editor
		// to display properly and receive key/mouse events.
		// Try to force the editor to resize and display itself fully.  See:
		//    https://groups.google.com/group/ace-discuss/browse_thread/thread/237262b521dcea33
		editor.resize();


//		if (typeof editor.completers === 'undefined') {
//            console.log("Completers: undefined")
//		} else {
//            console.log("Completers: " + editor.completers.length)
//		}
	}-*/;

    /**
     * Cleans up the entire editor.
     */
    public native void destroy() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.destroy();
	}-*/;

    /**
     * Set the theme.
     *
     * @param theme the theme (one of the values in the {@link AceEditorTheme}
     *              enumeration)
     */
    public void setTheme(final AceEditorTheme theme) {
        setThemeByName(theme.getName());
    }

    /**
     * Set the theme by name.
     *
     * @param themeName the theme name (e.g., "twilight")
     */
    public native void setThemeByName(String themeName) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setTheme("ace/theme/" + themeName);
	}-*/;

    /**
     * Set the mode.
     *
     * @param mode the mode (one of the values in the
     *             {@link AceEditorMode} enumeration)
     */
    public void setMode(final AceEditorMode mode) {
        setModeByName(mode.getName());
    }

    /**
     * Set the mode by name.
     *
     * @param shortModeName name of mode (e.g., "eclipse")
     */
    public native void setModeByName(String shortModeName) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var modeName = "ace/mode/" + shortModeName;
		var TheMode = $wnd.ace.require(modeName).Mode;
		editor.getSession().setMode(new TheMode());
	}-*/;

    /**
     * Enable a worker for the current session.
     *
     * @param useWorker true to enable a worker otherwise false
     */
    public native void setUseWorker(boolean useWorker) /*-{
                var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
                editor.getSession().setUseWorker(useWorker);
        }-*/;

    /**
     * Register a handler for change events generated by the editor.
     *
     * @param callback the change event handler
     */
    public native void addOnChangeHandler(AceEditorCallback callback) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().on("change", function(e) {
			callback.@edu.ycp.cs.dh.acegwt.client.ace.AceEditorCallback::invokeAceCallback(Lcom/google/gwt/core/client/JavaScriptObject;)(e);
		});
	}-*/;

    /**
     * Register a handler for cursor position change events generated by the editor.
     *
     * @param callback the cursor position change event handler
     */
    public native void addOnCursorPositionChangeHandler(AceEditorCallback callback) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().selection.on("changeCursor", function(e) {
			callback.@edu.ycp.cs.dh.acegwt.client.ace.AceEditorCallback::invokeAceCallback(Lcom/google/gwt/core/client/JavaScriptObject;)(e);
		});
	}-*/;

    /**
     * Give font size
     *
     * @return font size
     */
    public native int getFontSize() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getFontSize();
	}-*/;

    /**
     * Set integer font size.
     *
     * @param fontSize the font size to set, e.g., 16
     */
    public native void setFontSize(int fontSize) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setFontSize(fontSize);
	}-*/;

    /**
     * Set font size.
     *
     * @param fontSize the font size to set, e.g., "16px"
     */
    public native void setFontSize(String fontSize) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setFontSize(fontSize);
	}-*/;

    /**
     * Get the complete text in the editor as a String.
     *
     * @return the text in the editor
     */
    public native String getText() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getValue();
	}-*/;

    /**
     * Set the complete text in the editor from a String.
     *
     * @param text the text to set in the editor
     */
    public native void setText(String text) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().setValue(text);

		// If you don't set the scroll position then the scroll bar sizes don't get reevaluated.
		editor.renderer.scrollToX(0);
	}-*/;

    /**
     * Causes the editor to gain input focus.
     */
    public native void focus() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.focus();
	}-*/;

    /**
     * Retrieves the number of lines in the editor.
     *
     * @return The number of lines in the editor.
     */
    public native int getLineCount() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.session.getLength();
	}-*/;

    /**
     * Get the line of text at the given row number.
     *
     * @param row the row number
     * @return the line of text at that row number
     */
    public native String getLine(int row) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getDocument().getLine(row);
	}-*/;

    /**
     * Insert given text at the cursor.
     *
     * @param text text to insert at the cursor
     */
    public native void insertAtCursor(String text) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.insert(text);
	}-*/;

    /**
     * Get the current cursor position.
     *
     * @return the current cursor position
     */
    public native AceEditorCursorPosition getCursorPosition() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var pos = editor.getCursorPosition();
		return this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::getCursorPositionImpl(DD)(pos.row, pos.column);
	}-*/;

    private AceEditorCursorPosition getCursorPositionImpl(final double row, final double column) {
        return new AceEditorCursorPosition((int) row, (int) column);
    }

    /**
     * Gets the given document position as a zero-based index.
     *
     * @param position the position to obtain the absolute index of (base zero)
     * @return An index to the current location in the document
     */
    public int getIndexFromPosition(final AceEditorCursorPosition position) {
        return getIndexFromPositionImpl(position.toJsObject());
    }

    private native int getIndexFromPositionImpl(JavaScriptObject jsPosition) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getDocument().positionToIndex(jsPosition);
	}-*/;

    /**
     * Gets a document position from a supplied zero-based index.
     *
     * @param index (base zero)
     * @return A position object showing the row and column of the supplied index in the document
     */
    public native AceEditorCursorPosition getPositionFromIndex(int index) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var jsPosition = editor.getSession().getDocument().indexToPosition(index);
		return @edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition::create(II)(
			jsPosition.row,
			jsPosition.column
		);
	}-*/;

    /**
     * Set whether or not soft tabs should be used.
     *
     * @param useSoftTabs true if soft tabs should be used, false otherwise
     */
    public native void setUseSoftTabs(boolean useSoftTabs) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().setUseSoftTabs(useSoftTabs);
	}-*/;

    /**
     * Set tab size.  (Default is 4.)
     *
     * @param tabSize the tab size to set
     */
    public native void setTabSize(int tabSize) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().setTabSize(tabSize);
	}-*/;

    /**
     * Go to given line.
     *
     * @param line the line to go to
     */
    public native void gotoLine(int line) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.gotoLine(line);
	}-*/;

    /**
     * Move the selection to given row, col.
     *
     * @param row the row to go to
     * @param col the col to go to
     */
    public native void moveTo(int row, int col) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.selection.moveTo(row, col);
	}-*/;

    /**
     * Set whether or not the horizontal scrollbar is always visible.
     *
     * @param hScrollBarAlwaysVisible true if the horizontal scrollbar is always
     *                                visible, false if it is hidden when not needed
     */
    public native void setHScrollBarAlwaysVisible(boolean hScrollBarAlwaysVisible) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.renderer.setHScrollBarAlwaysVisible(hScrollBarAlwaysVisible);
	}-*/;

    /**
     * Set whether or not the gutter is shown.
     *
     * @param showGutter true if the gutter should be shown, false if it should be hidden
     */
    public native void setShowGutter(boolean showGutter) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.renderer.setShowGutter(showGutter);
	}-*/;

    /**
     * Set or unset read-only mode.
     *
     * @param readOnly true if editor should be set to readOnly, false if the
     *                 editor should be set to read-write
     */
    public native void setReadOnly(boolean readOnly) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setReadOnly(readOnly);
	}-*/;

    /**
     * Set or unset highlighting of currently selected word.
     *
     * @param highlightSelectedWord true to highlight currently selected word, false otherwise
     */
    public native void setHighlightSelectedWord(boolean highlightSelectedWord) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setHighlightSelectedWord(highlightSelectedWord);
	}-*/;

    /**
     * Set or unset the visibility of the print margin.
     *
     * @param showPrintMargin true if the print margin should be shown, false otherwise
     */
    public native void setShowPrintMargin(boolean showPrintMargin) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.renderer.setShowPrintMargin(showPrintMargin);
	}-*/;

    /**
     * Add an annotation to a the local <code>annotations</code> JsArray&lt;AceAnnotation&gt;, but does not set it on the editor
     *
     * @param row    to which the annotation should be added
     * @param column to which the annotation applies
     * @param text   to display as a tooltip with the annotation
     * @param type   to be displayed (one of the values in the
     *               {@link AceAnnotationType} enumeration)
     */
    public void addAnnotation(final int row, final int column, final String text, final AceAnnotationType type) {
        final AceAnnotation aceAnnotation = AceAnnotation.create(row, column, text, type.getName());

        // If you find the gutter markers not showing after uplifting the Ace version then grep
        // for 'this.setAnnotations = function(annotations) {' in ace.js as we have customised it
        // for stroom. You will need to compare it to an older version to get our custom code back

//        GWT.log("Adding annotation of type " + type.getName() + " to row " + row + " with text: " + text);
        annotations.push(aceAnnotation);
    }

    /**
     * Set any annotations which have been added via <code>addAnnotation</code> on the editor
     */
    public native void setAnnotations() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var annotations = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::annotations;
		editor.getSession().setAnnotations(annotations);
	}-*/;

    /**
     * Clear any annotations from the editor and reset the local <code>annotations</code> JsArray&lt;AceAnnotation&gt;
     */
    public native void clearAnnotations() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().clearAnnotations();
		this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::resetAnnotations()();
	}-*/;

    /**
     * Reset any annotations in the local <code>annotations</code> JsArray<AceAnnotation>
     */
    private void resetAnnotations() {
        annotations = JavaScriptObject.createArray().cast();
    }

    /**
     * Remove a command from the editor.
     *
     * @param command the command (one of the values in the
     *                {@link AceCommand} enumeration)
     */
    public void removeCommand(final AceCommand command) {
        removeCommandByName(command.getName());
    }

    /**
     * Execute a command with no arguments. See {@link AceCommand}
     * values for example.
     *
     * @param command the command (one of the values in the
     *                {@link AceCommand} enumeration)
     */
    public void execCommand(final AceCommand command) {
        execCommand(command, null);
    }

    /**
     * Execute a command with arguments (in case args is not null).
     * See {@link AceCommand} values for example.
     *
     * @param command the command (one of the values in the
     *                {@link AceCommand} enumeration)
     * @param args    command arguments (string or map)
     */
    public void execCommand(final AceCommand command, final AceCommandArgs args) {
        execCommand(command.getName(), args);
    }

    /**
     * Execute a command possibly containing string argument.
     *
     * @param command the command which could be one or two words separated
     *                by whitespaces
     */
    public native void execCommand(String command) /*-{
		var parts = command.split(/\s+/);
		this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::execCommand(Ljava/lang/String;Ljava/lang/String;)(parts[0], parts[1]);
	}-*/;

    /**
     * Execute a command with arguments (in case args is not null).
     *
     * @param command one word command
     * @param arg     command argument
     */
    public void execCommand(final String command, final String arg) {
        execCommandHidden(command, arg);
    }

    /**
     * Execute a command with arguments (in case args is not null).
     *
     * @param command one word command
     * @param args    command arguments of type {@link AceCommandArgs}
     */
    public void execCommand(final String command, final AceCommandArgs args) {
        execCommandHidden(command, args);
    }

    private native void execCommandHidden(String command, Object args) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (args && typeof args !== "string")
			args = args.@edu.ycp.cs.dh.acegwt.client.ace.AceCommandArgs::getValue()();
		editor.commands.exec(command, editor, args);
		editor.focus();
	}-*/;

    /**
     * Remove commands, that may not be required, from the editor
     *
     * @param command to be removed, one of
     *                "gotoline", "findnext", "findprevious", "find", "replace", "replaceall"
     */
    public native void removeCommandByName(String command) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.commands.removeCommand(command);
	}-*/;

//	public native void beautify() /*-{
//        var beautify = $wnd.ace.require("ace/ext/beautify");
//		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
//		beautify.beautify(editor.session);
//	}-*/;

    /**
     * Construct java wrapper for registered Ace command.
     *
     * @param command name of command
     * @return command description
     */
    public native AceCommandDescription getCommandDescription(String command) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var obj = editor.commands.commands[command];
		if (!obj)
			return null;
		return @edu.ycp.cs.dh.acegwt.client.ace.AceCommandDescription::fromJavaScript(Lcom/google/gwt/core/client/JavaScriptObject;)(obj);
	}-*/;

    /**
     * List names of all Ace commands.
     *
     * @return names of all Ace commands
     */
    public native List<String> listCommands() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var ret = @java.util.ArrayList::new()();
		for (var command in editor.commands.commands)
			ret.@java.util.ArrayList::add(Ljava/lang/Object;)(command);
		return ret;
	}-*/;

    /**
     * Add user defined command.
     *
     * @param description command description
     */
    public native void addCommand(AceCommandDescription description) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var command = description.@edu.ycp.cs.dh.acegwt.client.ace.AceCommandDescription::toJavaScript(Ledu/ycp/cs/dh/acegwt/client/ace/AceEditor;)(this);
		editor.commands.addCommand(command);
	}-*/;

    public native void beautify() /*-{
        // Relies on ace/beautify/beautify.js from https://cdnjs.com/libraries/js-beautify

		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;

		var source = editor.getSession().getValue();
		var output;
		var opts = {};

		opts.brace_style = "collapse";
		opts.break_chained_methods = false;
		opts.comma_first = false;
		opts.e4x = false;
		opts.end_with_newline = false;
		opts.indent_char = " ";
		opts.indent_inner_html = false;
		opts.indent_scripts = "keep";
		opts.indent_size = "2";
		opts.jslint_happy = false;
		opts.keep_array_indentation = false;
		opts.max_preserve_newlines = "2";
		opts.preserve_newlines = true;
		opts.space_before_conditional = false;
		opts.unescape_strings = false;
		opts.wrap_line_length = "0";

		output = $wnd.js_beautify(source, opts);

		editor.getSession().setValue(output);
	}-*/;

    public native void setScrollMargin(double top, double bottom, double left, double right) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.renderer.setScrollMargin(top, bottom, left, right);
	}-*/;

    /**
     * Set whether to use wrap mode or not
     *
     * @param useWrapMode true if word wrap should be used, false otherwise
     */
    public native void setUseWrapMode(boolean useWrapMode) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().setUseWrapMode(useWrapMode);
	}-*/;


    /* (non-Javadoc)
     * @see com.google.gwt.user.client.ui.ResizeComposite#onResize()
     */
    @Override
    public native void onResize() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.resize();
	}-*/;

    @Override
    public String getValue() {
        return this.getText();
    }

    @Override
    public void setValue(final String value) {
        this.setText(value);
    }

//    /**
//     * Set whether or not autocomplete is enabled.
//     *
//     * @param b true if autocomplete should be enabled, false if not
//     */
//    public native void setAutocompleteEnabled(boolean b) /*-{
//		// See: https://github.com/ajaxorg/ace/wiki/How-to-enable-Autocomplete-in-the-Ace-editor
//		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
//		if (b) {
//			$wnd.ace.require("ace/ext/language_tools");
//			editor.setOptions({
//                enableBasicAutocompletion: true,
//                enableSnippets: true
//            });
//		} else {
//			editor.setOptions({
//                enableBasicAutocompletion: false,
//                enableSnippets: false
//            });
//		}
////		console.log("enableBasicAutocompletion " + editor.getOption("enableBasicAutocompletion"))
////		console.log("enableSnippets " + editor.getOption("enableSnippets"))
////		console.log("enableLiveAutocompletion " + editor.getOption("enableLiveAutocompletion"))
//	}-*/;

    /**
     * Set the first line number that will be shown in the editor.
     *
     * @param lineNumber the first line number that will be displayed
     */
    public native void setFirstLineNumber(int lineNumber) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setOption("firstLineNumber", lineNumber);
	}-*/;

    /**
     * Set the maximum number of lines
     *
     * @param maxLines the maximum number of lines in the editor
     */
    public native void setMaxLines(int maxLines) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.setOption("maxLines", maxLines);
	}-*/;

    /**
     * Adds a static marker into this editor.
     *
     * @param range   an {@link AceRange}.
     * @param clazz   a CSS class that must be applied to the marker.
     * @param type    an {@link AceMarkerType}.
     * @param inFront set to 'true' if the marker must be in front of the text, 'false' otherwise.
     * @return The marker ID. This id can be then use to remove a marker from the editor.
     */
    public native int addMarker(AceRange range, String clazz, AceMarkerType type, boolean inFront) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		var markerID = editor.getSession().addMarker(range, clazz, type.@edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType::getName()(), inFront);
		this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::addMarker(ILedu/ycp/cs/dh/acegwt/client/ace/AceRange;)(markerID, range);
		return markerID;
	}-*/;

    /**
     * Adds a floating marker into this editor (the marker follows lines changes as insertions, suppressions...).
     *
     * @param range an {@link AceRange}.
     * @param clazz a CSS class that must be applied to the marker.
     * @param type  an {@link AceMarkerType}.
     * @return The marker ID. This id can be then use to remove a marker from the editor.
     */
    public native int addFloatingMarker(AceRange range, String clazz, AceMarkerType type) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		range.start = editor.getSession().doc.createAnchor(range.start);
		range.end = editor.getSession().doc.createAnchor(range.end);
		return this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::addMarker(Ledu/ycp/cs/dh/acegwt/client/ace/AceRange;Ljava/lang/String;Ledu/ycp/cs/dh/acegwt/client/ace/AceMarkerType;Z)
		(
			range,
			clazz,
			type,
			false
		);
	}-*/;

    /**
     * Removes the marker with the specified ID.
     *
     * @param markerId the marker ID.
     */
    public native void removeMarker(int markerId) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().removeMarker(markerId);
		this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::removeRegisteredMarker(I)(markerId);
	}-*/;

    /**
     * Gets all the displayed markers.
     *
     * @return An unmodifiable Mapping between markerID and the displayed range.
     */
    public Map<Integer, AceRange> getMarkers() {
        return Collections.unmodifiableMap(this.markers);
    }

    /**
     * Remove all the displayed markers.
     */
    public void removeAllMarkers() {
        final Integer[] ids = this.markers.keySet().toArray(new Integer[0]);
        for (final Integer id : ids) {
            removeMarker(id);
        }
    }

    private void addMarker(final int id, final AceRange range) {
        markers.put(id, range);
    }

    private void removeRegisteredMarker(final int id) {
        final AceRange range = markers.remove(id);
        range.detach();
    }

    /**
     * Prepare a wrapper around Ace Selection object.
     *
     * @return a wrapper around Ace Selection object
     */
    public AceSelection getSelection() {
        if (selection == null) {
            selection = new AceSelection(getSelectionJS());
        }
        return selection;
    }

    private native JavaScriptObject getSelectionJS() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getSelection();
	}-*/;

    public native int getScrollTop() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getScrollTop();
	}-*/;

    public native void setScrollTop(final int scrollTop) /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().setScrollTop(scrollTop);
	}-*/;

    public native boolean isClean() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.getSession().getUndoManager().isClean();
	}-*/;

    public native void markClean() /*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		editor.getSession().getUndoManager().markClean();
	}-*/;

    /**
     * Bind command line and editor. For default implementation of command line
     * you can use <code> AceCommandLine cmdLine = new AceDefaultCommandLine(textBox) </code>
     * where textBox could be for instance standard GWT TextBox or TextArea.
     *
     * @param cmdLine implementation of command line
     */
    public void initializeCommandLine(final AceCommandLine cmdLine) {
        this.commandLine = cmdLine;
        this.commandLine.setCommandLineListener(command -> execCommand(command));
    }

    public native Element getTextInputElement()/*-{
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		return editor.textInput.getElement();
	}-*/;
}

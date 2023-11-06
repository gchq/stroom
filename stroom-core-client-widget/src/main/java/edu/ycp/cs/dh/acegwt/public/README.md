You can find Ace release builds here https://github.com/ajaxorg/ace-builds/releases.
Use the content of `src` directory.
If we every have issues with conflicts with the globally named things like `define` and `require` then
we can switch to using the `src-src-noconflict`.
Ideally we would use the minified version but as we have to modify it we can't.

Currently using `1.5.0` of Ace.
To check the version, look for `exports.version = ` in `ace.js`.

For Stroom to be able to display four different annotation icons in the left hand gutter the source for `ace.js` has been altered. Any future update to `ace.js` will need to incorporate this change.

The original `ace.js` lines 15705-15711:
```
            var type = annotation.type;
            if (type == "error")
                rowInfo.className = " ace_error";
            else if (type == "warning" && rowInfo.className != " ace_error")
                rowInfo.className = " ace_warning";
            else if (type == "info" && (!rowInfo.className))
                rowInfo.className = " ace_info";
```

The replacement needed to allow custom annotation class names:
```
            rowInfo.className = " " + annotation.type;
```

Also to make code snippets work this was changed in AceEditor.java:

``` java 
    public native void setAutocompleteEnabled(boolean b) /*-{
		// See: https://github.com/ajaxorg/ace/wiki/How-to-enable-Autocomplete-in-the-Ace-editor
		var editor = this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditor::editor;
		if (b) {
			$wnd.ace.require("ace/ext/language_tools");
			editor.setOptions({
                enableBasicAutocompletion: true,
                enableLiveAutocompletion: true,
                enableSnippets: true
            });
		} else {
			editor.setOptions({
                enableBasicAutocompletion: false,
                enableLiveAutocompletion: false,
                enableSnippets: false
            });
		}
	}-*/;
```

These methods were added to expose ace features to java (in AceEditor.java)
Search for `Added for Stroom by at055612 START`

``` java 
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
            editor.setKeyboardHandler('ace/keyboard/vim');
        } else {
            editor.setKeyboardHandler(null);
        }
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
        editor.session.replace(range, text);
	}-*/;
```

The file mode-stroom_expression.js was added.

The mode STROOM_EXPRESSION was added to AceEditorMode.

To use other ACE modes you need to include them in here `stroom-core/src/main/resources/stroom/core/servlet/app.html`

If you need to add snippets to an existing mode that does not have any snippets
then you need to add something like this to the `mode-*.js` file so it knows where to
find the snippets file:
``` js
    this.snippetFileId = "ace/snippets/xml";
```

For examples of how to add snippets see `snippets/(xml|stroom_query|markdown).js`

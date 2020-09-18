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

Also to make code snippets work this was changed:

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

These methods were added to expose ace features to java

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
```
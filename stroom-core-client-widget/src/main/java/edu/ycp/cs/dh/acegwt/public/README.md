You can find Ace release builds here https://github.com/ajaxorg/ace-builds/releases.
Use the content of `src` directory.
If we every have issues with conflicts with the globally named things like `define` and `require` then
we can switch to using the `src-src-noconflict`.

This is the one copy of Ace in the repository: the GWT build copies it to `stroom-app/src/main/resources/ui/stroom/ace`
and `ui/dashboard/ace`, which are build outputs and not tracked.

Currently using `1.44.0` of Ace (`src` of the npm package `ace-builds`).
To check the version, look for `exports.version = ` in `ace.js`.

`ace.js` is unaltered. Stroom shows four different annotation icons in the left hand gutter by giving each annotation
a `className` (`stroom_info`, `stroom_warning`, `stroom_error` or `stroom_fatal_error`, see `AceAnnotationType`), which
Ace adds to the row's gutter cell, and `code-editor.css` draws the icons. Ace labels a gutter annotation for screen readers
only when its class is one of Ace's own, so `AceEditor.startEditor` labels Stroom's after each render of the gutter.

To upgrade Ace:
* Copy everything in `src` over the `ace` folder, except `snippets/xml.js` and `snippets/markdown.js`, which are Stroom's.
* Keep Stroom's own files, which aren't in Ace: the `mode-stroom_*.js` modes, the `snippets/stroom_*.js` snippets
  and `beautify/beautify.min.js`.
* In `mode-xml.js` and `mode-markdown.js` (which embeds the XML mode), add this line after
  `this.$id = "ace/mode/xml";`, so that the XML mode uses Stroom's XML (XSLT) snippets:
  ``` js
      this.snippetFileId = "ace/snippets/xml"; // Stroom: its XML (XSLT) snippets
  ```
* Run the workbench's `Widgets/Editors & Viewers/AceEditor*` stories, and compare their screenshots, before and after.

Perl 6 was removed from Ace (it is now Raku) and Stroom doesn't use it.

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

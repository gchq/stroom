package edu.ycp.cs.dh.acegwt.client.ace;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Represents an AceAnnotation object
 */
class AceAnnotation extends JavaScriptObject {

    protected AceAnnotation() {
    }

    /// @param type      Ace's annotation type ("info", "warning" or "error").
    /// @param className Stroom's gutter class for it, which Ace uses in place of its own.
    public static native AceAnnotation create(int row, int column, String text, String type,
                                              String className) /*-{
        // Ace's gutter adds className (after its leading space) to the annotation's row
        return {row: row, column: column, text: text, type: type, className: " " + className};
	}-*/;
}

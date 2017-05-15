package edu.ycp.cs.dh.acegwt.client.ace;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Represents an AceAnnotation object
 */
class AceAnnotation extends JavaScriptObject {
	protected AceAnnotation() {}

	public static native AceAnnotation create(int row, int column, String text, String type) /*-{
		return {row: row, column: column, text: text, type: type};
	}-*/;
}

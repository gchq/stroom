package edu.ycp.cs.dh.acegwt.client.ace;

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Represents an Ace Range Object.
 */
public final class AceRange extends JavaScriptObject {

    protected AceRange() {
    }

    /**
     * @param startRow    One based, inclusive
     * @param startColumn Zero based, inclusive
     * @param endRow      One based, inclusive
     * @param endColumn   Zero based, exclusive
     */
    public static AceRange create(final int startRow, final int startColumn, final int endRow, final int endColumn) {
        return toJsObject(startRow, startColumn, endRow, endColumn).cast();
    }

    /**
     * @return creates a new Range object.
     */
    static native JavaScriptObject toJsObject(int startRow, int startColumn, int endRow, int endColumn) /*-{
		var Range = $wnd.require('ace/range').Range;
		var range = new Range(startRow, startColumn, endRow, endColumn);
		return range;
	}-*/;

    /**
     * Detaches both, start and end from this {@link AceRange}.
     */
    public void detach() {
        detachStart();
        detachEnd();
    }

    /**
     * Detaches the start anchor from this {@link AceRange}.
     */
    public native void detachStart() /*-{
        if (typeof this.start != 'undefined' && typeof this.start != 'object') {
			this.start.detach();
		}
	}-*/;

    /**
     * Detaches the end achor from this {@link AceRange}.
     */
    public native void detachEnd() /*-{
		if (typeof this.end != 'undefined' && typeof this.end != 'object') {
			this.end.detach();
		}
	}-*/;
}

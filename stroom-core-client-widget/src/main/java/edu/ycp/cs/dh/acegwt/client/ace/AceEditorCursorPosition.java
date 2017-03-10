// Copyright (c) 2011-2014, David H. Hovemeyer <david.hovemeyer@gmail.com>
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

import com.google.gwt.core.client.JavaScriptObject;

/**
 * Represents a cursor position.
 */
public class AceEditorCursorPosition {
	private final int row, column;
	
	/**
	 * Constructor.
	 * 
	 * @param row     row (0 for first row)
	 * @param column  column (0 for first column)
	 */
	public AceEditorCursorPosition(int row, int column) {
		this.row = row;
		this.column = column;
	}
	
	/**
	 * @return the row (0 for first row)
	 */
	public int getRow() {
		return row;
	}
	
	/**
	 * @return the column (0 for first column)
	 */
	public int getColumn() {
		return column;
	}
	
	@Override
	public String toString() {
		return row + ":" + column;
	}
	
	/**
	 * Static creation method.
	 * This is handy for calling from JSNI code.
	 * 
	 * @param row     the row
	 * @param column  the column
	 * @return the {@link AceEditorCursorPosition}
	 */
	public static AceEditorCursorPosition create(int row, int column) {
		return new AceEditorCursorPosition(row, column);
	}
	
	/**
	 * Convert to a native Ace JavaScript position object
	 * (with integer-valued <code>row</code> and <code>column</code> fields.)
	 * 
	 * @return native Ace JavaScript position object
	 */
	public native JavaScriptObject toJsObject() /*-{
		return {
			row: this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition::row,
			column: this.@edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition::column
		};
	}-*/;
}

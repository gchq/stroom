// Copyright (c) 2011-2014, David H. Hovemeyer <david.hovemeyer@gmail.com>
// Copyright (c) 2014, Chris Ainsley <takapa@gmail.com>
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


/**
 * A segment of a completion snippet
 * */
public class AceCompletionSnippetSegmentTabstopItem implements AceCompletionSnippetSegment {
	
	private String tabstopText;

	/**
	 * Text that should fit inside a tabstop, the first tabstop is selected after a substitution, and subsequent tabstops are moved between by
	 * pressing the tab button. Tabstops are identified using an id.
	 * @param tabstopText The literal text that makes up part of the tab stop. This does not need to be escaped, escaping will be handled automatically.
	 */
	public AceCompletionSnippetSegmentTabstopItem(String tabstopText) {
		this.tabstopText = tabstopText;
	}
	
	@Override
	public String getPreparedText(int tabstopNumber) {
		
		// Special characters need escaping so that we can support tokens, see demo to see how this works in practice
		
		final String escapedText =
				tabstopText
					.replace("\\", "\\\\") // backslash becomes double backslash 
					.replace("$", "\\$")   // dollar becomes backslash dollar
					.replace("}", "\\}");  // right brace becones backslash right brace
		return "${" + tabstopNumber + ":" + escapedText + "}";
	}
	
}
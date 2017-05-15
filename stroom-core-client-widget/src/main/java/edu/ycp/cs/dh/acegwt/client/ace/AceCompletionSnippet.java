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

import com.google.gwt.core.client.JavaScriptObject;

/**
 * A completion proposed by an {@link AceCompletionProvider}. This particular implementation
 * allows for tabstops to be defines post-sunstitution.<br><br>This is useful when providing substitutions with
 * a default value in the centre of the substituted text value that typically has to be overwritten by the user or 
 * when substituting several values that should be modified by the user.<br><br>
 * 
 * There are two different constructors, a simple constructor that trust the user to manually escape the snippet text, and
 * a constructor where the escaping and tokenization is managed.<br><br>
 * 
 * <strong>Warning</strong>: this is an experimental feature of AceGWT.
 * It is possible that the API will change in an incompatible way
 * in future releases.
 */
public class AceCompletionSnippet extends AceCompletion {
	
	
	/**
	 * The caption of the completion (this is the left aligned autocompletion name on the left side of items in the dropdown box. If only a single completion is available in a context, then the caption will not be seen.
	 */
	private final String caption;
	
	/**
	 * The snippet text of the substitution, this should be in the format start-${0:snippetText}-after-${1:nexttabstop}-after. $ and backslash should be escaped with a leading backslash
	 */
	private final String snippet;
	
	
	/**
	 * "meta" means the category of the substitution (this appears right aligned on the dropdown list). This is freeform description and can contain anything but typically a very short category description (9 chars or less) such as "function" or "param" or "template".
	 */
	private final String meta;

	/**
	 * The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to the top. Items with an identical score are sorted alphbetically by caption in the drop down.
	 */
	private final int score;
	
	/**
	 * The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to the top. Items with an identical score are sorted alphbetically by caption in the drop down.
	 */
	private final String tooltip;


	/**
	 * Creates a snippet type substitution. A snippet-type substitution allows for tab-stop regions to be defined, in which text is automatically selected immediately the 
	 * autocomplete substitution. Multiple regions may be defined to be tabbed through for user editing.<br><br>This may be useful if a substiution is a template type subsitution where
	 * several areas of the template typically need to be edited always after a substitution. Being able to tab through the regions rather than having to manually locate and edit
	 * is a lot faster when dealing with potentially repeated text editing usage patterns.
	 * 
	 * @param caption The caption of the completion (this is the left aligned autocompletion name on the left side of items in the dropdown box. If only a single completion is available in a context, then the caption will not be seen.
	 * @param snippetSegments  The segments that make up this snippet. None of these segments needs to be escaped, this will be handled automatically.
	 * @param meta  "meta" means the category of the substitution (this appears right aligned on the dropdown list). This is freeform description and can contain anything but typically a very short category description (9 chars or less) such as "function" or "param" or "template".
	 * @param htmlTooltip the HTML tooltip
	 * @param score The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to the top. Items with an identical score are sorted alphbetically by caption in the drop down.
	 */
	public AceCompletionSnippet(String caption, AceCompletionSnippetSegment[] snippetSegments, String meta, String htmlTooltip, int score) {
		this.caption = caption;
		this.score = score;
		this.meta = meta;
		this.tooltip = htmlTooltip;
		
		StringBuilder sb = new StringBuilder();
		
		int tabStopNumber = 1;
		
		for (AceCompletionSnippetSegment segment : snippetSegments) {
			sb.append(segment.getPreparedText(tabStopNumber));
			if (segment instanceof AceCompletionSnippetSegmentTabstopItem) {
				tabStopNumber++;
			}
		}
		
		this.snippet = sb.toString();
	}
	
	/**
	 * Creates a snippet type substitution. A snippet-type substitution allows for tab-stop regions to be defined, in which text is automatically selected immediately the 
	 * autocomplete substitution. Multiple regions may be defined to be tabbed through for user editing.<br><br>This may be useful if a substiution is a template type subsitution where
	 * several areas of the template typically need to be edited always after a substitution. Being able to tab through the regions rather than having to manually locate and edit
	 * is a lot faster when dealing with potentially repeated text editing usage patterns.
	 * 
	 * <br><br>
	 * <strong>NOTE :: This is the advanced version of the completion snippet, where escaping and tokenization must be done manually.<br><br>It is recommended to use the alternative {@link #AceCompletionSnippet(String, AceCompletionSnippetSegment[], String, String, int)} constructor for 99% of use-cases. This raw version is provided as a convenience.</strong>
	 * 
	 * @param caption The caption of the completion (this is the left aligned autocompletion name on the left side of items in the dropdown box. If only a single completion is available in a context, then the caption will not be seen.
	 * @param snippet  the snippet text of the substitution, this should be in the format start-${0:snippetText}-after-${1:nexttabstop}-after. $ and backslash and rbrace should be escaped with a leading backslash
	 * @param meta  "meta" means the category of the substitution (this appears right aligned on the dropdown list). This is freeform description and can contain anything but typically a very short category description (9 chars or less) such as "function" or "param" or "template".
	 * @param score The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to the top. Items with an identical score are sorted alphbetically by caption in the drop down.
	 * @param htmlTooltip the HTML tooltip
	 */
	public AceCompletionSnippet(String caption, String snippet, int score, String meta, String htmlTooltip) {
		this.caption = caption;
		this.score = score;
		this.meta = meta;
		this.snippet = snippet;
		this.tooltip = htmlTooltip;
	}
	
	
	
	/**
	 * Convert to a native JS object in the format expected
	 * by the Ace code completion callback.<br><br>
	 * 
	 *  <strong>NOTE:</strong>: When providing snippet based substitutions, we use the 'caption' attribute rather than the 'name' attribute to describe the name that appears 
	 *  in the substitution selection list. This is not a bug in the wrapper, but rather a requirement of the ACE library itself.... see <a href="https://groups.google.com/forum/#!topic/ace-discuss/M4vw4XdVzBU">Ace Editor AutoComplete Tooltip + Web Link</a>
	 * @return native JS object
	 */
	native JavaScriptObject toJsObject() /*-{
		return {
			caption: this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet::caption,
			snippet: this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet::snippet,
			score: this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet::score,
			meta: this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet::meta,
			aceGwtHtmlTooltip: this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet::tooltip
		};
	}-*/;
}
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
import com.google.gwt.core.client.JsArray;

/**
 * Implementation of {@link AceCompletionCallback}
 * that delegates to a native JavaScript Ace code completion
 * callback.
 */
class AceCompletionCallbackImpl implements AceCompletionCallback {
	private JavaScriptObject jsCallback;
	
	public AceCompletionCallbackImpl(JavaScriptObject jsCallback) {
		this.jsCallback = jsCallback;
	}
	
	@Override
	public void invokeWithCompletions(AceCompletion[] proposals) {
		JsArray<JavaScriptObject> jsProposals = JavaScriptObject.createArray().cast();
		for (AceCompletion proposal : proposals) {
			jsProposals.push(proposal.toJsObject());
		}
		doInvokeWithCompletions(jsProposals);
	}
	
	private native void doInvokeWithCompletions(JsArray<JavaScriptObject> jsProposals) /*-{
		var callback = this.@edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallbackImpl::jsCallback;
		callback(null, jsProposals);
	}-*/;
}
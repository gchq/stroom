/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.gwt.user.client.ui;

import com.google.gwt.dom.client.Element;
import com.google.gwt.text.shared.testing.PassthroughParser;
import com.google.gwt.text.shared.testing.PassthroughRenderer;

/**
 * Abstract base class for most text entry widgets.
 */
public class TextBoxBase extends ValueBoxBase<String> {

    /**
     * Creates a text box that wraps the given browser element handle. This is
     * only used by subclasses.
     *
     * @param elem the browser element to wrap
     */
    protected TextBoxBase(final Element elem) {
        super(elem, PassthroughRenderer.instance(), PassthroughParser.instance());
    }

    /**
     * Overridden to return "" from an empty text box.
     */
    @Override
    public String getValue() {
        final String raw = super.getValue();
        return raw == null
                ? ""
                : raw;
    }

    /**
     * Legacy wrapper for {@link ValueBoxBase.TextAlignment}, soon to be deprecated.
     *
     * @deprecated use {@link #setAlignment(ValueBoxBase.TextAlignment)}
     */
    @Deprecated
    public static class TextAlignConstant {

        private TextAlignment value;

        private TextAlignConstant(final TextAlignment value) {
            this.value = value;
        }

        TextAlignment getTextAlignString() {
            return value;
        }
    }
}

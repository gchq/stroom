/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.ai.impl.tools.generation;

/// An XPath expression [XPathTreeParser] doesn't read: a syntax error, an unknown function, a function called with
/// the wrong number of arguments, or a prefix the stylesheet doesn't bind. Its message names the XPath error code
/// where there is one (XPST0003, XPST0017, XPST0081).
public final class XPathParseException extends RuntimeException {

    /// @param message What is wrong.
    public XPathParseException(final String message) {
        super(message);
    }
}

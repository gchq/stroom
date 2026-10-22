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

import java.util.Objects;

/// An XML comment in an [XmlElement]'s children (lxml's `etree.Comment`).
public final class XmlComment extends XmlNode {

    private final String text;

    /// @param text The comment's text, written as it is between `<!--` and `-->`.
    public XmlComment(final String text) {
        this.text = Objects.requireNonNullElse(text, "");
    }

    /// @return The comment's text.
    public String text() {
        return text;
    }
}

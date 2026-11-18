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

/// A node of the small XML tree the translation generator builds its XSLT in: elements, comments and processing
/// instructions are an element's children, and text is held as an element's `text` (before its first child) and
/// each child's `tail` (after it). The generated text is stable to the byte (the golden XSLTs pin it), so the tree
/// keeps what the serialisation depends on (namespace declarations and where they are, attribute order, text and
/// tails). See [XmlSerialiser] and [XmlParser].
public abstract sealed class XmlNode permits XmlElement, XmlComment, XmlInstruction {

    private XmlElement parent;
    private String tail;

    /// Made by the subclasses.
    XmlNode() {
        // Made by the subclasses.
    }

    /// @return The element holding this node, or null.
    public XmlElement parent() {
        return parent;
    }

    /// @param parent The element holding this node, or null.
    void setParent(final XmlElement parent) {
        this.parent = parent;
    }

    /// @return The text after this node, before its next sibling (its `tail`), or null.
    public String tail() {
        return tail;
    }

    /// @param tail The text after this node, or null for none.
    public void setTail(final String tail) {
        this.tail = tail;
    }
}

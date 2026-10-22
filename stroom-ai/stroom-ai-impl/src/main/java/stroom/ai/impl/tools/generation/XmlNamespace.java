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

/// A namespace declaration on an [XmlElement] (`xmlns:prefix="uri"`, or `xmlns="uri"` with no prefix), as libxml2
/// holds one: elements and attributes point at the declaration they are written with, so two declarations of the
/// same namespace are told apart by identity, not by value.
public final class XmlNamespace {

    private final String prefix;
    private final String uri;

    /// @param prefix The prefix, or null for the default namespace.
    /// @param uri    The namespace URI.
    XmlNamespace(final String prefix, final String uri) {
        this.prefix = prefix;
        this.uri = Objects.requireNonNull(uri, "uri");
    }

    /// @return The prefix, or null for the default namespace.
    public String prefix() {
        return prefix;
    }

    /// @return The namespace URI.
    public String uri() {
        return uri;
    }

    @Override
    public String toString() {
        return prefix == null
                ? "xmlns=\"" + uri + "\""
                : "xmlns:" + prefix + "=\"" + uri + "\"";
    }
}

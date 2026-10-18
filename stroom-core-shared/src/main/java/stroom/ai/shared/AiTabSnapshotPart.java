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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// A part of what the user sees in a tab, as text, e.g. an XSLT's code as edited, or a query's StroomQL.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiTabSnapshotPart {

    /// The part holding a document's text content, e.g. an XSLT's code or a dictionary's words.
    public static final String DATA = "data";
    /// The part holding a document's Documentation tab, as Markdown.
    public static final String DOCUMENTATION = "documentation";
    /// The part holding a query's StroomQL, as in its editor.
    public static final String QUERY = "query";
    /// The part holding a dictionary's imports, as edited.
    public static final String IMPORTS = "imports";
    /// The part describing a dashboard's components, as edited.
    public static final String DASHBOARD = "dashboard";

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String mediaType;
    @JsonProperty
    private final String text;

    /// @param name      What the part is, e.g. [#DATA] or [#QUERY].
    /// @param mediaType The kind of text, e.g. `application/xml`, or null if not known.
    /// @param text      The text, as the user sees it.
    @JsonCreator
    public AiTabSnapshotPart(@JsonProperty("name") final String name,
                             @JsonProperty("mediaType") final String mediaType,
                             @JsonProperty("text") final String text) {
        this.name = name;
        this.mediaType = mediaType;
        this.text = text;
    }

    /// @return What the part is, e.g. [#DATA] or [#QUERY].
    public String getName() {
        return name;
    }

    /// @return The kind of text, e.g. `application/xml`, or null if not known.
    public String getMediaType() {
        return mediaType;
    }

    /// @return The text, as the user sees it.
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiTabSnapshotPart that = (AiTabSnapshotPart) o;
        return Objects.equals(name, that.name)
               && Objects.equals(mediaType, that.mediaType)
               && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, mediaType, text);
    }

    @Override
    public String toString() {
        return "AiTabSnapshotPart{name='" + name + "', mediaType='" + mediaType + "', length="
               + (text == null
                ? 0
                : text.length()) + "}";
    }
}

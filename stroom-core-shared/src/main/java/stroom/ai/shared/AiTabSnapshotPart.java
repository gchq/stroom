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
    /// The part holding the stepping screen's input pane for the selected element, as displayed.
    public static final String STEP_INPUT = "stepInput";
    /// The part holding the stepping screen's output pane for the selected element, as displayed.
    public static final String STEP_OUTPUT = "stepOutput";
    /// The part holding the selected element's code while stepping, as edited (unsaved changes included).
    public static final String STEP_CODE = "stepCode";

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

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

    /// A hash of some text that the browser and the server work out the same way (64-bit FNV-1a over each
    /// character's two bytes), e.g. to tell whether a dashboard has changed since a snapshot of it was taken.
    ///
    /// @param text Some text, or null.
    /// @return Its hash, as hex.
    public static String hashText(final String text) {
        final String value = text == null
                ? ""
                : text;
        long hash = FNV_OFFSET;
        for (int i = 0; i < value.length(); i++) {
            final char chr = value.charAt(i);
            hash ^= (chr & 0xff);
            hash *= FNV_PRIME;
            hash ^= ((chr >> 8) & 0xff);
            hash *= FNV_PRIME;
        }
        return Long.toHexString(hash);
    }

    /// A hash of some text's lines ([#hashText(String)]), each without the space around it, leaving out blank
    /// ones, so text laid out differently (e.g. indented, or with a final new line) hashes the same.
    ///
    /// @param text Some text, or null.
    /// @return Its hash, as hex.
    public static String hashLines(final String text) {
        final StringBuilder lines = new StringBuilder();
        for (final String line : (text == null
                ? ""
                : text).split("\n")) {
            final String stripped = line.strip();
            if (!stripped.isEmpty()) {
                lines.append(stripped).append('\n');
            }
        }
        return hashText(lines.toString());
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

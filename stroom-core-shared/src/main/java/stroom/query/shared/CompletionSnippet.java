/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A completion proposed by an {@link AceCompletionProvider}. This particular implementation
 * allows for tabstops to be defines post-sunstitution.<br><br>This is useful when providing substitutions with
 * a default value in the centre of the substituted text value that typically has to be overwritten by the user or
 * when substituting several values that should be modified by the user.<br><br>
 * <p>
 * There are two different constructors, a simple constructor that trust the user to manually escape the snippet
 * text, and a constructor where the escaping and tokenization is managed.<br><br>
 * <p>
 * <strong>Warning</strong>: this is an experimental feature of AceGWT.
 * It is possible that the API will change in an incompatible way
 * in future releases.
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class CompletionSnippet implements CompletionItem {

    /**
     * The caption of the completion (this is the left-aligned autocompletion name on the left side of items in the
     * dropdown box. If only a single completion is available in a context, then the caption will not be seen.
     */
    @JsonProperty
    private final String caption;

    /**
     * The snippet text of the substitution, this should be in the format
     * <pre>{@code start-${0:snippetText}-after-${1:nexttabstop}-after.}</pre>
     * $ and backslash should be escaped with a leading backslash.
     */
    @JsonProperty
    private final String snippet;

    /**
     * The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to
     * the top. Items with an identical score are sorted alphabetically by caption in the drop-down.
     */
    @JsonProperty
    private final int score;

    /**
     * "meta" means the category of the substitution (this appears right aligned on the dropdown list). This is freeform
     * description and can contain anything but typically a very short category description (9 chars or less) such as
     * "function" or "param" or "template".
     */
    @JsonProperty
    private final String meta;

    /**
     * The score is the value assigned to the autocompletion option. Scores with a higher value will appear closer to
     * the top. Items with an identical score are sorted alphabetically by caption in the drop-down.
     */
    @JsonProperty
    private final String tooltip;

    /**
     * Constructor.
     *
     * @param caption The caption of the completion (this is the left aligned autocompletion name on the left side of
     *                items in the dropdown box. If only a single completion is available in a context, then the caption
     *                will not be seen.
     * @param snippet the snippet text of the substitution, this should be in the format
     *                <pre>{@code start-${0:snippetText}-after-${1:nexttabstop}-after. }</pre>
     *                $ and backslash and rbrace should be escaped with a leading backslash
     * @param meta    "meta" means the category of the substitution (this appears right aligned on the dropdown list).
     *                This is freeform description and can contain anything but typically a very short category
     *                description (9 chars or less) such as "function" or "param" or "template".
     * @param tooltip "tooltip" is an escaped html tooltip to be displayed when the completion option is displayed, this
     *                can be null.
     * @param score   The score is the value assigned to the autocompletion option. Scores with a higher value will
     *                appear closer to the top. Items with an identical score are sorted alphabetically by caption in
     *                the drop-down.
     */
    @JsonCreator
    public CompletionSnippet(@JsonProperty("caption") final String caption,
                             @JsonProperty("snippet") final String snippet,
                             @JsonProperty("score") final int score,
                             @JsonProperty("meta") final String meta,
                             @JsonProperty("tooltip") final String tooltip) {
        this.caption = caption;
        this.snippet = snippet;
        this.score = score;
        this.meta = meta;
        this.tooltip = tooltip;
    }

    public String getCaption() {
        return caption;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getMeta() {
        return meta;
    }

    public int getScore() {
        return score;
    }

    public String getTooltip() {
        return tooltip;
    }
}

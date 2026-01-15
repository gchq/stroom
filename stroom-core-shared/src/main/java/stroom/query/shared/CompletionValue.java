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

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public final class CompletionValue implements CompletionItem {

    /**
     * The caption of the completion (this is the left-aligned autocompletion name on the left side of items in the
     * dropdown box. If only a single completion is available in a context, then the caption will not be seen.
     */
    @JsonProperty
    private final String caption;

    /**
     * The text value of the completion. This does not need to be escaped.
     */
    @JsonProperty
    private final String value;

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
     * @param value   The text value of the completion. This does not need to be escaped.
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
    public CompletionValue(@JsonProperty("caption") final String caption,
                           @JsonProperty("value") final String value,
                           @JsonProperty("score") final int score,
                           @JsonProperty("meta") final String meta,
                           @JsonProperty("tooltip") final String tooltip) {
        this.caption = caption;
        this.value = value;
        this.score = score;
        this.meta = meta;
        this.tooltip = tooltip;
    }

    @Override
    public String getCaption() {
        return caption;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String getMeta() {
        return meta;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public String getTooltip() {
        return tooltip;
    }
}

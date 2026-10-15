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

package stroom.query.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Either the quick filter text for the tree, or the reason there is none - a disabled item, a
 * condition the syntax cannot spell, a field the surface does not declare. The reason is written
 * for the user of the dialog.
 */
@JsonInclude(Include.NON_NULL)
public class FormatQuickFilterResult {

    @JsonProperty
    private final String text;
    @JsonProperty
    private final String error;

    @JsonCreator
    public FormatQuickFilterResult(@JsonProperty("text") final String text,
                                   @JsonProperty("error") final String error) {
        this.text = text;
        this.error = error;
    }

    public static FormatQuickFilterResult of(final String text) {
        return new FormatQuickFilterResult(text, null);
    }

    public static FormatQuickFilterResult failed(final String error) {
        return new FormatQuickFilterResult(null, error);
    }

    public String getText() {
        return text;
    }

    public String getError() {
        return error;
    }

    @JsonIgnore
    public boolean isOk() {
        return error == null;
    }

    @Override
    public String toString() {
        return "FormatQuickFilterResult{" +
               "text='" + text + '\'' +
               ", error='" + error + '\'' +
               '}';
    }
}

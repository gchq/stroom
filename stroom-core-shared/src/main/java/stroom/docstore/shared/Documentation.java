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

package stroom.docstore.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@SuppressWarnings("ClassCanBeRecord") // cos GWT
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class Documentation {

    public static final Documentation EMPTY = new Documentation(null);

    @JsonProperty
    private final String markdown;

    @JsonCreator
    public Documentation(@JsonProperty("markdown") final String markdown) {
        this.markdown = markdown;
    }

    public static Documentation of(final String markdown) {
        if (NullSafe.isBlankString(markdown)) {
            return EMPTY;
        } else {
            return new Documentation(markdown);
        }
    }

    public String getMarkdown() {
        return markdown;
    }
}

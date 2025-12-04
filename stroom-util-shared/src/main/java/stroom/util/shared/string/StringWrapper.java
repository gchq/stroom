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

package stroom.util.shared.string;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This is to get round our client rest code not liking plain text output.
 * Think we need to change it to use TextCallback
 * rather than MethodCallback for text content.
 */
@JsonInclude(Include.NON_NULL)
public class StringWrapper {

    @JsonProperty
    private final String string;

    @JsonCreator
    public StringWrapper(@JsonProperty("string") final String string) {
        this.string = string;
    }

    public static StringWrapper wrap(final String string) {
        return new StringWrapper(string);
    }

    public String getString() {
        return string;
    }

    @Override
    public String toString() {
        return string;
    }
}

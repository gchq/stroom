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

package stroom.util.shared.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/// An HTTP header sent with every request a configured client makes, e.g. Anthropic's `anthropic-version`.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class HttpHeader {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final String value;

    /// @param name  The header's name.
    /// @param value The header's value.
    @JsonCreator
    public HttpHeader(@JsonProperty("name") final String name,
                      @JsonProperty("value") final String value) {
        this.name = name;
        this.value = value;
    }

    /// @return The header's name.
    public String getName() {
        return name;
    }

    /// @return The header's value.
    public String getValue() {
        return value;
    }

    /// HTTP header names are not case-sensitive, so 'X-Tenant' and 'x-tenant' are the same header.
    ///
    /// @param headers The headers, or null.
    /// @return The name of the first header given more than once, ignoring case and surrounding spaces, or null
    /// if there is none. Headers with a blank name are ignored.
    public static String findDuplicateName(final List<HttpHeader> headers) {
        if (headers == null) {
            return null;
        }
        final Set<String> seen = new HashSet<>();
        for (final HttpHeader header : headers) {
            if (header != null && header.getName() != null && !header.getName().trim().isEmpty()) {
                final String name = header.getName().trim();
                if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                    return name;
                }
            }
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpHeader that = (HttpHeader) o;
        return Objects.equals(name, that.name)
               && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        // Not the value, which may be a secret.
        return name;
    }
}

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

package stroom.quickfilter.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Identifies whose-filters-these-are for {@link QuickFilterHistoryResource}: the field set a
 * quick filter parses against and, where the screen queries one document at a time, which
 * document. The user is implicit - it is always the caller.
 * <p>
 * Derived from a {@link QuickFilterContext} by {@link QuickFilterContext#toHistoryKey()}; the
 * client never builds one by hand.
 */
@JsonInclude(Include.NON_NULL)
public class QuickFilterHistoryKey {

    /**
     * Stored, so bounded and validated: see {@link QuickFilterHistoryResource}.
     */
    public static final int MAX_CONTEXT_LENGTH = 64;

    @JsonProperty
    private final String context;
    @JsonProperty
    private final String dataSourceUuid;

    @JsonCreator
    public QuickFilterHistoryKey(@JsonProperty("context") final String context,
                                 @JsonProperty("dataSourceUuid") final String dataSourceUuid) {
        this.context = Objects.requireNonNull(context);
        // Empty rather than null: the unique key on the table has to include the data source and
        // MySQL does not treat NULLs as equal, so every no-data-source row must say so the same way.
        this.dataSourceUuid = NullSafe.isBlankString(dataSourceUuid)
                ? ""
                : dataSourceUuid;
    }

    public String getContext() {
        return context;
    }

    /**
     * Never null; empty when the surface has no data source.
     */
    public String getDataSourceUuid() {
        return dataSourceUuid;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QuickFilterHistoryKey that = (QuickFilterHistoryKey) o;
        return context.equals(that.context) && dataSourceUuid.equals(that.dataSourceUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(context, dataSourceUuid);
    }

    @Override
    public String toString() {
        return "QuickFilterHistoryKey{" +
               "context='" + context + '\'' +
               ", dataSourceUuid='" + dataSourceUuid + '\'' +
               '}';
    }
}

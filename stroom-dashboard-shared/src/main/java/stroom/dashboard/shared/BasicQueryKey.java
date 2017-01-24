/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

public class BasicQueryKey implements QueryKey {
    private static final long serialVersionUID = -5219659648940501503L;

    private String queryId;

    public BasicQueryKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public BasicQueryKey(final String queryId) {
        this.queryId = queryId;
    }

    public String getQueryId() {
        return queryId;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof BasicQueryKey)) {
            return false;
        }

        final BasicQueryKey queryKey = (BasicQueryKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(queryId, queryKey.queryId);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(queryId);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return queryId;
    }
}

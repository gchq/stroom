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

public class UniqueQueryKey extends QueryKeyImpl {
    private static final long serialVersionUID = -3222989872764402068L;

    private String discriminator;

    public UniqueQueryKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public UniqueQueryKey(final long dashboardId, final String dashboardName, final String queryId,
                          final String discriminator) {
        super(dashboardId, dashboardName, queryId);
        this.discriminator = discriminator;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof UniqueQueryKey)) {
            return false;
        }

        final UniqueQueryKey queryKey = (UniqueQueryKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.appendSuper(super.equals(o));
        builder.append(discriminator, queryKey.discriminator);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.appendSuper(super.hashCode());
        builder.append(discriminator);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return super.toString() + " - " + discriminator;
    }
}

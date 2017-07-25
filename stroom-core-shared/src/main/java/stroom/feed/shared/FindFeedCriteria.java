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

package stroom.feed.shared;

import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

/**
 * Class used to find feed definitions.
 */
public class FindFeedCriteria extends FindDocumentEntityCriteria {
    public static final String FIELD_TYPE = "Type";
    public static final String FIELD_CLASSIFICATION = "Classification";
    private static final long serialVersionUID = 1L;
    private Boolean reference;
    private EntityIdSet<Feed> feedIdSet = new EntityIdSet<Feed>();

    public FindFeedCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindFeedCriteria(final String name) {
        super(name);
    }

    public Boolean getReference() {
        return reference;
    }

    public void setReference(final Boolean reference) {
        this.reference = reference;
    }

    public EntityIdSet<Feed> getFeedIdSet() {
        return feedIdSet;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.appendSuper(super.hashCode());
        builder.append(feedIdSet);
        builder.append(reference);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof FindFeedCriteria)) {
            return false;
        }

        final FindFeedCriteria ffc = (FindFeedCriteria) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.appendSuper(super.equals(o));
        builder.append(feedIdSet, ffc.feedIdSet);
        builder.append(reference, ffc.reference);
        return builder.isEquals();
    }
}

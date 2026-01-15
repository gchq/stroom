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

package stroom.receive.common;

import stroom.security.api.UserIdentity;

import java.util.Objects;

/**
 * {@link UserIdentity} obtained when authenticated by {@link HashedDataFeedKey}
 */
public class DataFeedKeyUserIdentity implements UserIdentity {

    public static final String SUBJECT_ID_PREFIX = "data-feed-key-";

    private final String subjectId;
    private final String displayName;

    public DataFeedKeyUserIdentity(final String keyOwner) {
        Objects.requireNonNull(keyOwner);
        this.subjectId = SUBJECT_ID_PREFIX + keyOwner;
        this.displayName = subjectId;
    }

    @Override
    public String subjectId() {
        return subjectId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return "DataFeedKeyUserIdentity{" +
               "subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final DataFeedKeyUserIdentity that = (DataFeedKeyUserIdentity) object;
        return Objects.equals(subjectId, that.subjectId) && Objects.equals(displayName,
                that.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, displayName);
    }
}

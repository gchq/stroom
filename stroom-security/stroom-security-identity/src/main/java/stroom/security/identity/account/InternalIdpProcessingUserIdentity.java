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

package stroom.security.identity.account;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;
import stroom.util.authentication.HasRefreshable;
import stroom.util.authentication.PerishableItem;
import stroom.util.authentication.RefreshableItem;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

public class InternalIdpProcessingUserIdentity implements HasRefreshable, UserIdentity, HasJwt {

    // The subject of the processing user identity
    public static final String INTERNAL_PROCESSING_USER = "INTERNAL_PROCESSING_USER";

    private final RefreshableItem<String> refreshableJws;

    public InternalIdpProcessingUserIdentity(final Duration refreshBuffer,
                                             final Supplier<PerishableItem<String>> jwsSupplier) {
        this.refreshableJws = new RefreshableItem<>(jwsSupplier, refreshBuffer);
    }

    @Override
    public String subjectId() {
        return INTERNAL_PROCESSING_USER;
    }

    @Override
    public String getJwt() {
        return refreshableJws.getItem();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InternalIdpProcessingUserIdentity that = (InternalIdpProcessingUserIdentity) o;
        return Objects.equals(subjectId(), that.subjectId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId());
    }

    @Override
    public String toString() {
        return subjectId();
    }

    @Override
    public RefreshableItem<String> getRefreshable() {
        return refreshableJws;
    }
}

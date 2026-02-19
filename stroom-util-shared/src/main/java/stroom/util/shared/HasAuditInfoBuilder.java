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

package stroom.util.shared;

public interface HasAuditInfoBuilder<T, B extends HasAuditInfoBuilder<T, ?>> extends Builder<T> {

    B createTimeMs(Long createTimeMs);

    B createUser(String createUser);

    B updateTimeMs(Long updateTimeMs);

    B updateUser(String updateUser);

    default Builder<T> createAudit(final HasAuditableUserIdentity hasAuditableUserIdentity) {
        return createAudit(hasAuditableUserIdentity.getUserIdentityForAudit());
    }

    default Builder<T> createAudit(final String user) {
        final long now = System.currentTimeMillis();
        return createTimeMs(now).updateTimeMs(now).createUser(user).updateUser(user);
    }

    default Builder<T> updateAudit(final HasAuditableUserIdentity hasAuditableUserIdentity) {
        return updateAudit(hasAuditableUserIdentity.getUserIdentityForAudit());
    }

    default Builder<T> updateAudit(final String user) {
        final long now = System.currentTimeMillis();
        return updateTimeMs(now).updateUser(user);
    }
}

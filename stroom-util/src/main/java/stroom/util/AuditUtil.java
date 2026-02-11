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

package stroom.util;

import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasAuditInfoBuilder;
import stroom.util.shared.HasAuditInfoGetters;
import stroom.util.shared.HasAuditableUserIdentity;

import java.util.function.Function;

public final class AuditUtil {

    private AuditUtil() {
        // Utility class.
    }

    /**
     * Stamp {@code hasAuditInfo} with the create/update user/time, with the user identity
     * provided by {@code hasAuditableUserIdentity}.
     */
    public static void stamp(final HasAuditableUserIdentity hasAuditableUserIdentity,
                             final HasAuditInfo hasAuditInfo) {
        final long now = System.currentTimeMillis();
        final String userIdentityForAudit = hasAuditableUserIdentity.getUserIdentityForAudit();

        if (hasAuditInfo.getCreateTimeMs() == null) {
            hasAuditInfo.setCreateTimeMs(now);
        }
        if (hasAuditInfo.getCreateUser() == null) {
            hasAuditInfo.setCreateUser(userIdentityForAudit);
        }
        hasAuditInfo.setUpdateTimeMs(now);
        hasAuditInfo.setUpdateUser(userIdentityForAudit);
    }

    /**
     * Stamp {@code hasAuditInfo} with the create/update user/time, with the user identity
     * provided by {@code hasAuditableUserIdentity}.
     */
    public static <T extends HasAuditInfoGetters, B extends HasAuditInfoBuilder<T, ?>> B stampNew(
            final HasAuditableUserIdentity hasAuditableUserIdentity,
            final B builder) {
        final long now = System.currentTimeMillis();
        final String userIdentityForAudit = hasAuditableUserIdentity.getUserIdentityForAudit();
        builder.createTimeMs(now);
        builder.createUser(userIdentityForAudit);
        builder.updateTimeMs(now);
        builder.updateUser(userIdentityForAudit);
        return builder;
    }

    /**
     * Stamp {@code hasAuditInfo} with the create/update user/time, with the user identity
     * provided by {@code hasAuditableUserIdentity}.
     */
    public static <T extends HasAuditInfoGetters, B extends HasAuditInfoBuilder<T, ?>> B stamp(
            final HasAuditableUserIdentity hasAuditableUserIdentity,
            final T doc,
            final B builder) {
        final long now = System.currentTimeMillis();
        final String userIdentityForAudit = hasAuditableUserIdentity.getUserIdentityForAudit();
        if (doc == null || doc.getCreateTimeMs() == null) {
            builder.createTimeMs(now);
        }
        if (doc == null || doc.getCreateUser() == null) {
            builder.createUser(userIdentityForAudit);
        }
        builder.updateTimeMs(now);
        builder.updateUser(userIdentityForAudit);
        return builder;
    }

    /**
     * Remove audit data from docs.
     *
     * @param builderFactory The builder factory that allows a builder to be created for the doc that can remove the
     *                       fields.
     * @param doc            The doc to alter.
     * @param <D>            Doc type.
     * @param <B>            Builder type.
     * @return The doc with audit fields removed.
     */
    public static <D, B extends HasAuditInfoBuilder<D, ?>> D
    removeAuditData(final Function<D, B> builderFunction,
                    final D doc) {
        return builderFunction
                .apply(doc)
                .createTimeMs(null)
                .createUser(null)
                .updateTimeMs(null)
                .updateUser(null)
                .build();
    }
}

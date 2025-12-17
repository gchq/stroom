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
import stroom.util.shared.HasAuditableUserIdentity;

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
}

/*
 * Copyright 2017 Crown Copyright
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

package stroom.security;

import stroom.security.shared.UserIdentity;

public class SecurityHelper implements AutoCloseable {
    private volatile SecurityContext securityContext;
    private final Action action;

    private SecurityHelper(final SecurityContext securityContext, final Action action, final UserIdentity userIdentity) {
        this.securityContext = securityContext;
        this.action = action;

        if (securityContext != null) {
            switch (action) {
                case AS_USER:
                    securityContext.pushUser(userIdentity);
                    break;
                case ELEVATE:
                    securityContext.elevatePermissions();
                    break;
            }
        }
    }

    public static SecurityHelper asUser(SecurityContext securityContext, final UserIdentity userIdentity) {
        return new SecurityHelper(securityContext, Action.AS_USER, userIdentity);
    }

    public static SecurityHelper processingUser(SecurityContext securityContext) {
        return asUser(securityContext, ProcessingUserIdentity.INSTANCE);
    }

    public static SecurityHelper elevate(SecurityContext securityContext) {
        return new SecurityHelper(securityContext, Action.ELEVATE, null);
    }

    @Override
    public synchronized void close() {
        if (securityContext != null) {
            switch (action) {
                case AS_USER:
                    securityContext.popUser();
                    break;
                case ELEVATE:
                    securityContext.restorePermissions();
                    break;
            }
        }
        securityContext = null;
    }

    private enum Action {
        AS_USER, ELEVATE
    }
}

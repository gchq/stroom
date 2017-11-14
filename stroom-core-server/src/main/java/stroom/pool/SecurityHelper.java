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

package stroom.pool;

import stroom.security.SecurityContext;
import stroom.util.task.ServerTask;

public class SecurityHelper implements AutoCloseable {
    private volatile SecurityContext securityContext;

    private SecurityHelper(final SecurityContext securityContext) {
        this.securityContext = securityContext;
        if (securityContext != null) {
            securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
        }
    }

    public static SecurityHelper elevate(SecurityContext securityContext) {
        return new SecurityHelper(securityContext);
    }

    @Override
    public synchronized void close() {
        if (securityContext != null) {
            securityContext.popUser();
        }
        securityContext = null;
    }
}

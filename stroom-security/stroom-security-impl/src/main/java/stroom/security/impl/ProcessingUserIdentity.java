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

package stroom.security.impl;

import stroom.security.api.UserIdentity;

public final class ProcessingUserIdentity implements UserIdentity {
    private static final String INTERNAL_PROCESSING_USER = "INTERNAL_PROCESSING_USER";

    public static final UserIdentity INSTANCE = new ProcessingUserIdentity();

    private ProcessingUserIdentity() {
        // Utility class.
    }

    @Override
    public String getId() {
        return INTERNAL_PROCESSING_USER;
    }

    @Override
    public String getJws() {
        return null;
    }

    @Override
    public String getSessionId() {
        return null;
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        return o instanceof ProcessingUserIdentity;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}

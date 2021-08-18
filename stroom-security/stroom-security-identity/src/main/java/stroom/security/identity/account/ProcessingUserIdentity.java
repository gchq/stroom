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

package stroom.security.identity.account;

import stroom.security.api.HasJws;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class ProcessingUserIdentity implements UserIdentity, HasJws {

    public static final String INTERNAL_PROCESSING_USER = "INTERNAL_PROCESSING_USER";

    private String jws;

    public ProcessingUserIdentity() {
    }

    public ProcessingUserIdentity(final String jws) {
        this.jws = jws;
    }

    @Override
    public String getId() {
        return INTERNAL_PROCESSING_USER;
    }

    @Override
    public String getJws() {
        return jws;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessingUserIdentity that = (ProcessingUserIdentity) o;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return getId();
    }
}

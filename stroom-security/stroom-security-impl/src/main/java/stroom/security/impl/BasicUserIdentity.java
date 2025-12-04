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

package stroom.security.impl;

import stroom.security.api.UserIdentity;
import stroom.security.shared.HasUserRef;
import stroom.util.shared.UserRef;

import java.util.Objects;
import java.util.Optional;

public class BasicUserIdentity implements UserIdentity, HasUserRef {

    private final UserRef userRef;

    public BasicUserIdentity(final UserRef userRef) {
        Objects.requireNonNull(userRef);
        this.userRef = userRef;
    }

    @Override
    public String subjectId() {
        return userRef.getSubjectId();
    }

    @Override
    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String getDisplayName() {
        return userRef.toDisplayString();
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(userRef.getFullName());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BasicUserIdentity that = (BasicUserIdentity) o;
        return Objects.equals(userRef, that.userRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef);
    }

    @Override
    public String toString() {
        return "BasicUserIdentity{" +
               "userRef='" + userRef + '\'' +
               '}';
    }
}

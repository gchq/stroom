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

public class CertificateUserIdentity implements UserIdentity {

    private final String commonName;

    public CertificateUserIdentity(final String commonName) {
        this.commonName = Objects.requireNonNull(commonName);
    }

    @Override
    public String subjectId() {
        return commonName;
    }

    @Override
    public String toString() {
        return "CertificateUserIdentity{" +
               "commonName='" + commonName + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CertificateUserIdentity that = (CertificateUserIdentity) o;
        return Objects.equals(commonName, that.commonName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commonName);
    }
}

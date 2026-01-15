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

package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.UserIdentity;

import java.util.Objects;

public class DefaultOpenIdCredsUserIdentity implements UserIdentity, HasJwt {

    private final String id;
    private final String jwt;

    public DefaultOpenIdCredsUserIdentity(final String id, final String jwt) {
        this.id = id;
        this.jwt = jwt;
    }

    @Override
    public String getJwt() {
        return jwt;
    }

    @Override
    public String subjectId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOpenIdCredsUserIdentity that = (DefaultOpenIdCredsUserIdentity) o;
        return Objects.equals(id, that.id) && Objects.equals(jwt, that.jwt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jwt);
    }

    @Override
    public String toString() {
        return "DefaultOpenIdCredsUserIdentity{" +
               "id='" + id + '\'' +
               ", jwt='" + jwt + '\'' +
               '}';
    }
}

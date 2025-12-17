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

package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;

import org.jose4j.jwt.consumer.JwtContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Identity for a client sending data to stroom-proxy
 */
public class ProxyClientUserIdentity implements UserIdentity {

    private final String id;
    private final String displayName;
    private final String fullName;
    // debatable whether it is worth holding this or not
    private final JwtContext jwtContext;

    public ProxyClientUserIdentity(final String id,
                                   final String displayName,
                                   final String fullName,
                                   final JwtContext jwtContext) {
        this.id = id;
        this.displayName = Objects.requireNonNullElse(displayName, id);
        this.fullName = fullName;
        this.jwtContext = jwtContext;
    }

    @Override
    public String subjectId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(fullName);
    }

    public JwtContext getJwtContext() {
        return jwtContext;
    }

    @Override
    public String toString() {
        return "ProxyUserIdentity{" +
               "id='" + id + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullName='" + fullName + '\'' +
               '}';
    }
}

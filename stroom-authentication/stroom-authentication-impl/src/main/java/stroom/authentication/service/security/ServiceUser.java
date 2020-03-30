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

package stroom.authentication.service.security;

import com.google.common.base.Preconditions;

import javax.validation.constraints.NotNull;
import java.security.Principal;

public final class ServiceUser implements Principal {

    @NotNull
    private final String jwt;
    @NotNull
    private String name;

    public ServiceUser(@NotNull String name, @NotNull String jwt) {
        Preconditions.checkNotNull(name);
        Preconditions.checkNotNull(jwt);
        this.name = name;
        this.jwt = jwt;
    }

    @NotNull
    public String getName() {
        return this.name;
    }

    @NotNull
    public final String getJwt() {
        return this.jwt;
    }
}

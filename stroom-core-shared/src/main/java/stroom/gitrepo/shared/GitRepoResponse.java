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

package stroom.gitrepo.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Object to return from GitRepo REST API calls with whether the call worked
 * and any messages about what happened.
 */
@JsonInclude(Include.NON_NULL)
public class GitRepoResponse {

    @JsonProperty
    private final boolean ok;

    @JsonProperty
    private final String message;

    /**
     * Constructor.
     * @param ok      If the API call worked
     * @param message Any message. Must not be null.
     */
    @JsonCreator
    public GitRepoResponse(@JsonProperty("ok") final boolean ok,
                           @JsonProperty("message") final String message) {
        Objects.requireNonNull(message);
        this.ok = ok;
        this.message = message;
    }

    /**
     * @return true if call worked, false if there was an error.
     */
    public boolean isOk() {
        return ok;
    }

    /**
     * @return Any message associated with the response. Never returns null.
     */
    public String getMessage() {
        return message;
    }

}

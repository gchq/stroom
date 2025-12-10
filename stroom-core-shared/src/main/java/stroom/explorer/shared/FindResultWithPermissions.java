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

package stroom.explorer.shared;

import stroom.security.shared.DocumentUserPermissions;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FindResultWithPermissions {

    @JsonProperty
    private final FindResult findResult;
    @JsonProperty
    private final DocumentUserPermissions permissions;

    @JsonCreator
    public FindResultWithPermissions(@JsonProperty("findResult") final FindResult findResult,
                                     @JsonProperty("permissions") final DocumentUserPermissions permissions) {
        this.findResult = findResult;
        this.permissions = permissions;
    }

    public FindResult getFindResult() {
        return findResult;
    }

    public DocumentUserPermissions getPermissions() {
        return permissions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FindResultWithPermissions that = (FindResultWithPermissions) o;
        return Objects.equals(findResult, that.findResult);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(findResult);
    }

    @Override
    public String toString() {
        return NullSafe.toString(findResult);
    }
}

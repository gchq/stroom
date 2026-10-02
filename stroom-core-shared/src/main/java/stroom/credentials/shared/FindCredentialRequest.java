/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.shared;

import stroom.security.shared.DocumentPermission;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class FindCredentialRequest extends BaseCriteria {

    public static final CriteriaFieldSort DEFAULT_SORT =
            new CriteriaFieldSort(CredentialFields.CREDENTIAL_NAME, false, true);
    public static final List<CriteriaFieldSort> DEFAULT_SORT_LIST =
            Collections.singletonList(DEFAULT_SORT);

    @JsonProperty
    private final String filter;
    @JsonProperty
    private final Set<CredentialType> credentialTypes;
    @JsonProperty
    private final DocumentPermission requiredPermission;

    @JsonCreator
    public FindCredentialRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("filter") final String filter,
                                 @JsonProperty("credentialTypes") final Set<CredentialType> credentialTypes,
                                 @JsonProperty("requiredPermission") final DocumentPermission requiredPermission) {
        super(pageRequest, sortList);
        this.filter = filter;
        this.credentialTypes = credentialTypes;
        this.requiredPermission = requiredPermission;
    }

    public String getFilter() {
        return filter;
    }

    public Set<CredentialType> getCredentialTypes() {
        return credentialTypes;
    }

    public DocumentPermission getRequiredPermission() {
        return requiredPermission;
    }
}

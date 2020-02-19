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

package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.FindDocumentEntityCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;

import java.util.List;

/**
 * Criteria class.
 */
@JsonInclude(Include.NON_DEFAULT)
public class FindUserCriteria extends FindDocumentEntityCriteria {
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_LAST_LOGIN = "Last Login";

    /**
     * Find user groups
     */
    @JsonProperty
    private Boolean group;
    @JsonProperty
    private User relatedUser;

    public FindUserCriteria() {
    }

    public FindUserCriteria(final String name, final Boolean group) {
        super(name);
        this.group = group;
    }

    @JsonCreator
    public FindUserCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<Sort> sortList,
                            @JsonProperty("name") final StringCriteria name,
                            @JsonProperty("requiredPermission") final String requiredPermission,
                            @JsonProperty("group") final Boolean group,
                            @JsonProperty("relatedUser") final User relatedUser) {
        super(pageRequest, sortList, name, requiredPermission);
        this.group = group;
        this.relatedUser = relatedUser;
    }

    public FindUserCriteria(final Boolean group) {
        this.group = group;
    }

    public FindUserCriteria(final User relatedUser) {
        this.relatedUser = relatedUser;
    }

    public Boolean getGroup() {
        return group;
    }

    public void setGroup(final Boolean group) {
        this.group = group;
    }

    public User getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(User relatedUser) {
        this.relatedUser = relatedUser;
    }
}

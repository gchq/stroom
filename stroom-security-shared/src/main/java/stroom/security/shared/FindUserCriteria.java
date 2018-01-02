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

import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.Period;

/**
 * Criteria class.
 */
public class FindUserCriteria extends FindDocumentEntityCriteria {
    public static final String MANAGE_USERS_PERMISSION = "Manage Users";
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_LAST_LOGIN = "Last Login";
    /**
     * Order by types.
     */
    private static final long serialVersionUID = -6584216681046824635L;

    /**
     * Find by login valid period
     */
    private Period loginValidPeriod;

    /**
     * Find by last Login period
     */
    private Period lastLoginPeriod;

    /**
     * Find user groups
     */
    private Boolean group;

    private UserRef relatedUser;

    public FindUserCriteria() {
    }

    public FindUserCriteria(final String name, final Boolean group) {
        super(name);
        this.group = group;
    }

    public FindUserCriteria(final Boolean group) {
        this.group = group;
    }

    public FindUserCriteria(final UserRef relatedUser) {
        this.relatedUser = relatedUser;
    }

    public Period getLastLoginPeriod() {
        return lastLoginPeriod;
    }

    public void setLastLoginPeriod(final Period lastLoginPeriod) {
        this.lastLoginPeriod = lastLoginPeriod;
    }

    public Period getLoginValidPeriod() {
        return loginValidPeriod;
    }

    public void setLoginValidPeriod(final Period loginValidPeriod) {
        this.loginValidPeriod = loginValidPeriod;
    }

    public Boolean getGroup() {
        return group;
    }

    public void setGroup(final Boolean group) {
        this.group = group;
    }

    public UserRef getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(UserRef relatedUser) {
        this.relatedUser = relatedUser;
    }
}

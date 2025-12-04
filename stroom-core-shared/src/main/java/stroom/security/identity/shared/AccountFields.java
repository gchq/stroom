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

package stroom.security.identity.shared;

import stroom.query.api.datasource.QueryField;
import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccountFields {

    public static final String FIELD_NAME_USER_ID = "userid";
    public static final String FIELD_NAME_EMAIL = "email";
    public static final String FIELD_NAME_FIRST_NAME = "first";
    public static final String FIELD_NAME_LAST_NAME = "last";
    public static final String FIELD_NAME_STATUS = "status";
    public static final String FIELD_NAME_LAST_LOGIN_MS = "lastLoginMs";
    public static final String FIELD_NAME_LOGIN_FAILURES = "loginFailures";
    public static final String FIELD_NAME_COMMENTS = "comments";

    public static final QueryField FIELD_USER_ID = QueryField.createText(FIELD_NAME_USER_ID);
    public static final QueryField FIELD_EMAIL = QueryField.createText(FIELD_NAME_EMAIL);
    public static final QueryField FIELD_FIRST_NAME = QueryField.createText(FIELD_NAME_FIRST_NAME);
    public static final QueryField FIELD_LAST_NAME = QueryField.createText(FIELD_NAME_LAST_NAME);
    public static final QueryField FIELD_STATUS = QueryField.createText(FIELD_NAME_STATUS);
    public static final QueryField FIELD_COMMENTS = QueryField.createText(FIELD_NAME_COMMENTS);

    public static final FilterFieldDefinition FIELD_DEF_USER_ID = FilterFieldDefinition.defaultField(
            FIELD_NAME_USER_ID);
    public static final FilterFieldDefinition FIELD_DEF_EMAIL = FilterFieldDefinition.qualifiedField(
            FIELD_NAME_EMAIL);
    public static final FilterFieldDefinition FIELD_DEF_FIRST_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_NAME_FIRST_NAME);
    public static final FilterFieldDefinition FIELD_DEF_LAST_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_NAME_LAST_NAME);
    public static final FilterFieldDefinition FIELD_DEF_STATUS = FilterFieldDefinition.qualifiedField(
            FIELD_NAME_STATUS);
    public static final FilterFieldDefinition FIELD_DEF_COMMENTS = FilterFieldDefinition.qualifiedField(
            FIELD_NAME_COMMENTS);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = List.of(
            FIELD_DEF_USER_ID,
            FIELD_DEF_EMAIL,
            FIELD_DEF_FIRST_NAME,
            FIELD_DEF_LAST_NAME,
            FIELD_DEF_STATUS,
            FIELD_DEF_COMMENTS);

    public static final Set<QueryField> DEFAULT_FIELDS = Set.of(
            FIELD_USER_ID);

    public static final Map<String, QueryField> ALL_FIELDS_MAP = QueryField.buildFieldMap(
            FIELD_USER_ID,
            FIELD_EMAIL,
            FIELD_FIRST_NAME,
            FIELD_LAST_NAME,
            FIELD_STATUS,
            FIELD_COMMENTS);

    private AccountFields() {
    }
}

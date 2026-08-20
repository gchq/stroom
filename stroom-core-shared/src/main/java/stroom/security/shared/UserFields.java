/*
 * Copyright 2024 Crown Copyright
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

import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserFields {

    public static final String FIELD_IS_GROUP = "isgroup";
    public static final String FIELD_UNIQUE_ID = "id";
    //    public static final String FIELD_NAME = "name";
    public static final String FIELD_DISPLAY_NAME = "display";
    public static final String FIELD_FULL_NAME = "full";
    public static final String FIELD_ENABLED = "enabled";

    // IS_GROUP and ENABLED are converted by StringUtil::asBoolean before they reach SQL, so only
    // the exact-match conditions in DEFAULT_BOOLEAN are truthful for them. The three text fields
    // below are identity-mapped onto text columns in UserDaoImpl, so they can honour SQL_TEXT -
    // which matters now the quick filter is parsed server-side, because a bare term is CONTAINS.
    public static final QueryField IS_GROUP = QueryField.createBoolean(FIELD_IS_GROUP);
    //    public static final QueryField NAME = QueryField.createText(FIELD_NAME);
    public static final QueryField DISPLAY_NAME = QueryField.createSqlText(FIELD_DISPLAY_NAME);
    public static final QueryField UNIQUE_ID = QueryField.createSqlText(FIELD_UNIQUE_ID);
    public static final QueryField FULL_NAME = QueryField.createSqlText(FIELD_FULL_NAME);
    public static final QueryField ENABLED = QueryField.createBoolean(FIELD_ENABLED);
    /**
     * Will return all direct members of the group with a UUID matching the term's value
     */
    public static final QueryField CHILDREN_OF = QueryField
            .builder()
            .fldName("ChildrenOf")
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    /**
     * Will return all groups for which the term value's uuid is a member.
     */
    public static final QueryField PARENTS_OF = QueryField
            .builder()
            .fldName("ParentsOf")
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();

    public static final FilterFieldDefinition FIELD_DEF_DISPLAY_NAME = FilterFieldDefinition.defaultField(
            FIELD_DISPLAY_NAME);
    public static final FilterFieldDefinition FIELD_DEF_UNIQUE_ID = FilterFieldDefinition.qualifiedField(
            FIELD_UNIQUE_ID);
    public static final FilterFieldDefinition FIELD_DEF_IS_GROUP = FilterFieldDefinition.qualifiedField(FIELD_IS_GROUP);
    //    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_FULL_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_FULL_NAME);
    public static final FilterFieldDefinition FIELD_DEF_ENABLED = FilterFieldDefinition.qualifiedField(
            FIELD_ENABLED);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_UNIQUE_ID,
            FIELD_DEF_ENABLED,
            FIELD_DEF_IS_GROUP,
//            FIELD_DEF_NAME,
            FIELD_DEF_DISPLAY_NAME,
            FIELD_DEF_FULL_NAME);

    public static final Set<QueryField> DEFAULT_FIELDS = Set.of(
            DISPLAY_NAME,
            UNIQUE_ID);

    /**
     * The fields the quick filter resolves against, server side. CHILDREN_OF and PARENTS_OF are
     * excluded: they are structural terms the client composes into the criteria's expression, not
     * something a user types.
     */
    public static final List<QueryField> QUICK_FILTER_DEFAULT_FIELDS = Arrays.asList(
            DISPLAY_NAME,
            UNIQUE_ID);

    public static final List<QueryField> QUICK_FILTER_FIELDS = Arrays.asList(
            IS_GROUP,
            UNIQUE_ID,
            DISPLAY_NAME,
            FULL_NAME,
            ENABLED);

    public static final Map<String, QueryField> ALL_FIELDS_MAP = QueryField.buildFieldMap(
            IS_GROUP,
            UNIQUE_ID,
//            NAME,
            DISPLAY_NAME,
            FULL_NAME,
            ENABLED,
            CHILDREN_OF,
            PARENTS_OF);
}

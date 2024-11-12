package stroom.security.shared;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserFields {

    public static final String FIELD_IS_GROUP = "group";
    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DISPLAY_NAME = "display";
    public static final String FIELD_FULL_NAME = "full";

    public static final QueryField IS_GROUP = QueryField.createBoolean(FIELD_IS_GROUP);
    public static final QueryField NAME = QueryField.createText(FIELD_NAME);
    public static final QueryField DISPLAY_NAME = QueryField.createText(FIELD_DISPLAY_NAME);
    public static final QueryField ID = QueryField.createText(FIELD_ID);
    public static final QueryField FULL_NAME = QueryField.createText(FIELD_FULL_NAME);
    public static final QueryField GROUP_CONTAINS = QueryField
            .builder()
            .fldName("GroupContains")
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField PARENT_GROUP = QueryField
            .builder()
            .fldName("ParentGroup")
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();

    public static final FilterFieldDefinition FIELD_DEF_IS_GROUP = FilterFieldDefinition.qualifiedField(FIELD_IS_GROUP);
    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_DISPLAY_NAME = FilterFieldDefinition.defaultField(
            FIELD_DISPLAY_NAME);
    public static final FilterFieldDefinition FIELD_DEF_FULL_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_FULL_NAME);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_IS_GROUP,
            FIELD_DEF_NAME,
            FIELD_DEF_DISPLAY_NAME,
            FIELD_DEF_FULL_NAME);


    public static final Set<QueryField> DEFAULT_FIELDS = new HashSet<>();
    public static final Map<String, QueryField> ALL_FIELD_MAP = new HashMap<>();

    static {
        DEFAULT_FIELDS.add(DISPLAY_NAME);
        DEFAULT_FIELDS.add(NAME);

        ALL_FIELD_MAP.put(IS_GROUP.getFldName(), IS_GROUP);
        ALL_FIELD_MAP.put(NAME.getFldName(), NAME);
        ALL_FIELD_MAP.put(DISPLAY_NAME.getFldName(), DISPLAY_NAME);
        ALL_FIELD_MAP.put(FULL_NAME.getFldName(), FULL_NAME);
        ALL_FIELD_MAP.put(GROUP_CONTAINS.getFldName(), GROUP_CONTAINS);
        ALL_FIELD_MAP.put(PARENT_GROUP.getFldName(), PARENT_GROUP);
    }
}

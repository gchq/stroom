package stroom.security.shared;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.util.shared.string.CIKey;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserFields {

    public static final String FIELD_IS_GROUP = "group";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DISPLAY_NAME = "display";
    public static final String FIELD_FULL_NAME = "full";

    public static final QueryField IS_GROUP = QueryField.createBoolean(
            CIKey.ofStaticKey(FIELD_IS_GROUP), true);
    public static final QueryField NAME = QueryField.createText(CIKey.ofStaticKey(FIELD_NAME), true);
    public static final QueryField DISPLAY_NAME = QueryField.createText(
            CIKey.ofStaticKey(FIELD_DISPLAY_NAME), true);
    public static final QueryField FULL_NAME = QueryField.createText(
            CIKey.ofStaticKey(FIELD_FULL_NAME), true);
    public static final QueryField GROUP_CONTAINS = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("GroupContains"))
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField PARENT_GROUP = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("ParentGroup"))
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

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = List.of(
            FIELD_DEF_IS_GROUP,
            FIELD_DEF_NAME,
            FIELD_DEF_DISPLAY_NAME,
            FIELD_DEF_FULL_NAME);

    public static final Set<QueryField> DEFAULT_FIELDS = new HashSet<>(List.of(
            DISPLAY_NAME,
            NAME));

    public static final Map<CIKey, QueryField> ALL_FIELD_MAP = Stream.of(
                    IS_GROUP,
                    NAME,
                    DISPLAY_NAME,
                    FULL_NAME,
                    GROUP_CONTAINS,
                    PARENT_GROUP)
            .collect(Collectors.toMap(
                    QueryField::getFldNameAsCIKey,
                    Function.identity()));
}

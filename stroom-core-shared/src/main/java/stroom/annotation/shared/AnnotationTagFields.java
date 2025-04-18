package stroom.annotation.shared;

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationTagFields {

    String ID = "Id";
    String UUID = "UUID";
    String NAME = "Name";
    String TYPE_ID = "TypeId";

    QueryField ID_FIELD = QueryField.createId(ID);
    QueryField UUID_FIELD = QueryField.createId(UUID);
    QueryField NAME_FIELD = QueryField.createDate(NAME);
    QueryField TYPE_ID_FIELD = QueryField.createText(TYPE_ID);

    List<QueryField> FIELDS = Arrays.asList(
            ID_FIELD,
            UUID_FIELD,
            NAME_FIELD,
            TYPE_ID_FIELD);
    Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
}

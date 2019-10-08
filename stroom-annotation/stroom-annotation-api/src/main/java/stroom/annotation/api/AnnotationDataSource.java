package stroom.annotation.api;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationDataSource {
    String ANNOTATION_PERMISSION = "Annotation";

    String NAMESPACE = "annotation";
    String ANNOTATION_FIELD_PREFIX = NAMESPACE + ":";
    String CREATE_USER = ANNOTATION_FIELD_PREFIX + "CreatedBy";
    String STATUS = ANNOTATION_FIELD_PREFIX + "Status";
    String ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";

    DataSourceField CREATED_BY_FIELD = new DataSourceField.Builder().name(CREATE_USER).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField STATUS_FIELD = new DataSourceField.Builder().name(STATUS).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField ASSIGNED_TO_FIELD = new DataSourceField.Builder().name(ASSIGNED_TO).type(DataSourceFieldType.FIELD).queryable(true).build();

    List<DataSourceField> FIELDS = Arrays.asList(CREATED_BY_FIELD, STATUS_FIELD, ASSIGNED_TO_FIELD);
    Map<String, DataSourceField> FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
}

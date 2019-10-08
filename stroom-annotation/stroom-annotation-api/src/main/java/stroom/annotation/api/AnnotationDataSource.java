package stroom.annotation.api;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;

import java.util.Arrays;
import java.util.List;

public interface AnnotationDataSource {
    String ANNOTATION_PERMISSION = "Annotation";

    String NAMESPACE = "annotation";
    String CREATE_USER = NAMESPACE + ":CreatedBy";
    String STATUS = NAMESPACE + ":Status";
    String ASSIGNED_TO = NAMESPACE + ":AssignedTo";

    DataSourceField CREATED_BY_FIELD = new DataSourceField.Builder().name(CREATE_USER).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField STATUS_FIELD = new DataSourceField.Builder().name(STATUS).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField ASSIGNED_TO_FIELD = new DataSourceField.Builder().name(ASSIGNED_TO).type(DataSourceFieldType.FIELD).queryable(true).build();

    List<DataSourceField> FIELDS = Arrays.asList(CREATED_BY_FIELD, STATUS_FIELD, ASSIGNED_TO_FIELD);
}

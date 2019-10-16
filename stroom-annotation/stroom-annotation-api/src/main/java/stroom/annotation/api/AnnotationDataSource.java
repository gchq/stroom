package stroom.annotation.api;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.index.shared.IndexConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationDataSource {
    String ANNOTATION_PERMISSION = "Annotation";

    String CURRENT_USER_FUNCTION = "currentUser()";

    String NAMESPACE = "annotation";
    String ANNOTATION_FIELD_PREFIX = NAMESPACE + ":";
    String ID = ANNOTATION_FIELD_PREFIX + "Id";
    String CREATED_BY = ANNOTATION_FIELD_PREFIX + "CreatedBy";
    String TITLE = ANNOTATION_FIELD_PREFIX + "Title";
    String SUBJECT = ANNOTATION_FIELD_PREFIX + "Subject";
    String STATUS = ANNOTATION_FIELD_PREFIX + "Status";
    String ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";
    String COMMENT = ANNOTATION_FIELD_PREFIX + "Comment";
    String HISTORY = ANNOTATION_FIELD_PREFIX + "History";

    DataSourceField ID_FIELD = new DataSourceField.Builder().name(ID).type(DataSourceFieldType.ID).queryable(true).build();
    DataSourceField STREAM_ID_FIELD = new DataSourceField.Builder().name(IndexConstants.STREAM_ID).type(DataSourceFieldType.ID).queryable(true).build();
    DataSourceField EVENT_ID_FIELD = new DataSourceField.Builder().name(IndexConstants.EVENT_ID).type(DataSourceFieldType.ID).queryable(true).build();
    DataSourceField CREATED_BY_FIELD = new DataSourceField.Builder().name(CREATED_BY).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField TITLE_FIELD = new DataSourceField.Builder().name(TITLE).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField SUBJECT_FIELD = new DataSourceField.Builder().name(SUBJECT).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField STATUS_FIELD = new DataSourceField.Builder().name(STATUS).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField ASSIGNED_TO_FIELD = new DataSourceField.Builder().name(ASSIGNED_TO).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField COMMENT_FIELD = new DataSourceField.Builder().name(COMMENT).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField HISTORY_FIELD = new DataSourceField.Builder().name(HISTORY).type(DataSourceFieldType.FIELD).queryable(true).build();

    List<DataSourceField> FIELDS = Arrays.asList(ID_FIELD, STREAM_ID_FIELD, EVENT_ID_FIELD, CREATED_BY_FIELD, TITLE_FIELD, SUBJECT_FIELD, STATUS_FIELD, ASSIGNED_TO_FIELD, COMMENT_FIELD, HISTORY_FIELD);
    Map<String, DataSourceField> FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
}

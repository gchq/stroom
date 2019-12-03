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
    String CREATED_ON = ANNOTATION_FIELD_PREFIX + "CreatedOn";
    String CREATED_BY = ANNOTATION_FIELD_PREFIX + "CreatedBy";
    String UPDATED_ON = ANNOTATION_FIELD_PREFIX + "UpdatedOn";
    String UPDATED_BY = ANNOTATION_FIELD_PREFIX + "UpdatedBy";
    String TITLE = ANNOTATION_FIELD_PREFIX + "Title";
    String SUBJECT = ANNOTATION_FIELD_PREFIX + "Subject";
    String STATUS = ANNOTATION_FIELD_PREFIX + "Status";
    String ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";
    String COMMENT = ANNOTATION_FIELD_PREFIX + "Comment";
    String HISTORY = ANNOTATION_FIELD_PREFIX + "History";

    DataSourceField ID_FIELD = new DataSourceField.Builder().name(ID).type(DataSourceFieldType.ID_FIELD).queryable(true).build();
//    DataSourceField STREAM_ID_FIELD = new DataSourceField.Builder().name(IndexConstants.STREAM_ID).type(DataSourceFieldType.ID_FIELD).queryable(true).build();
//    DataSourceField EVENT_ID_FIELD = new DataSourceField.Builder().name(IndexConstants.EVENT_ID).type(DataSourceFieldType.ID_FIELD).queryable(true).build();
    DataSourceField CREATED_ON_FIELD = new DataSourceField.Builder().name(CREATED_ON).type(DataSourceFieldType.DATE_FIELD).queryable(true).build();
    DataSourceField CREATED_BY_FIELD = new DataSourceField.Builder().name(CREATED_BY).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField UPDATED_ON_FIELD = new DataSourceField.Builder().name(UPDATED_ON).type(DataSourceFieldType.DATE_FIELD).queryable(true).build();
    DataSourceField UPDATED_BY_FIELD = new DataSourceField.Builder().name(UPDATED_BY).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField TITLE_FIELD = new DataSourceField.Builder().name(TITLE).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField SUBJECT_FIELD = new DataSourceField.Builder().name(SUBJECT).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField STATUS_FIELD = new DataSourceField.Builder().name(STATUS).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField ASSIGNED_TO_FIELD = new DataSourceField.Builder().name(ASSIGNED_TO).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField COMMENT_FIELD = new DataSourceField.Builder().name(COMMENT).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();
    DataSourceField HISTORY_FIELD = new DataSourceField.Builder().name(HISTORY).type(DataSourceFieldType.TEXT_FIELD).queryable(true).build();

    List<DataSourceField> FIELDS = Arrays.asList(
            ID_FIELD,
//            STREAM_ID_FIELD,
//            EVENT_ID_FIELD,
            CREATED_ON_FIELD,
            CREATED_BY_FIELD,
            UPDATED_ON_FIELD,
            UPDATED_BY_FIELD,
            TITLE_FIELD,
            SUBJECT_FIELD,
            STATUS_FIELD,
            ASSIGNED_TO_FIELD,
            COMMENT_FIELD,
            HISTORY_FIELD);
    Map<String, DataSourceField> FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
}

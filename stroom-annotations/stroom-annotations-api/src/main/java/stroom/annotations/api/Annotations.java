package stroom.annotations.api;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.search.coprocessor.Coprocessors;
import stroom.search.coprocessor.Values;
import stroom.util.shared.HasTerminate;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

public interface Annotations {
   String ANNOTATIONS_PERMISSION = "Annotations";

    String USER = "annotations:User";
    String COMMENT = "annotations:Comment";
    String STATUS = "annotations:Status";
    String ASSIGNEE = "annotations:Assignee";

    DataSourceField USER_FIELD = new DataSourceField.Builder().name(USER).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField COMMENT_FIELD = new DataSourceField.Builder().name(COMMENT).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField STATUS_FIELD = new DataSourceField.Builder().name(STATUS).type(DataSourceFieldType.FIELD).queryable(true).build();
    DataSourceField ASSIGNEE_FIELD = new DataSourceField.Builder().name(ASSIGNEE).type(DataSourceFieldType.FIELD).queryable(true).build();

    List<DataSourceField> FIELDS = Arrays.asList(USER_FIELD, COMMENT_FIELD, STATUS_FIELD, ASSIGNEE_FIELD);
}

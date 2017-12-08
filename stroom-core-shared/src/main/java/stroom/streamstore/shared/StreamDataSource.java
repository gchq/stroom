package stroom.streamstore.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;

public class StreamDataSource {
    public static final String STREAM_STORE_TYPE = "StreamStore";
    public static final DocRef STREAM_STORE_DOC_REF = new DocRef.Builder()
            .type(STREAM_STORE_TYPE)
            .uuid("0")
            .name(STREAM_STORE_TYPE)
            .build();

    private static final List<DataSourceField> FIELDS = new ArrayList<>();
    public static List<DataSourceField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    private static final List<DataSourceField> EXTENDED_FIELDS = new ArrayList<>();

    public static List<DataSourceField> getExtendedFields() {
        return new ArrayList<>(EXTENDED_FIELDS);
    }

    public static final String FEED = "Feed";
    public static final String PIPELINE = "Pipeline";
    public static final String STREAM_TYPE = "Stream Type";
    public static final String STREAM_ID = "Stream Id";
    public static final String PARENT_STREAM_ID = "Parent Stream Id";
    public static final String CREATED = "Creation time";
    public static final String EFFECTIVE = "Effective time";
    public static final String STATUS = "Status";
    public static final String STATUS_TIME = "Status time";

    // Extended fields.
    public static final String NODE = "Node";
    public static final String REC_READ = "RecRead";
    public static final String REC_WRITE = "RecWrite";
    public static final String REC_INFO = "RecInfo";
    public static final String REC_WARN = "RecWarn";
    public static final String REC_ERROR = "RecError";
    public static final String REC_FATAL = "RecFatal";
    public static final String DURATION = "Duration";
    public static final String FILE_SIZE = "FileSize";
    public static final String STREAM_SIZE = "StreamSize";

    static {
        FIELDS.add(createStringField(FEED));
        FIELDS.add(createStringField(PIPELINE));
        FIELDS.add(createStringField(STREAM_TYPE));
        FIELDS.add(createIdField(STREAM_ID));
        FIELDS.add(createIdField(PARENT_STREAM_ID));
        FIELDS.add(createDateField(CREATED));
        FIELDS.add(createDateField(EFFECTIVE));
        FIELDS.add(createDateField(STATUS_TIME));

        EXTENDED_FIELDS.addAll(FIELDS);

        EXTENDED_FIELDS.add(createStringField(NODE));
        EXTENDED_FIELDS.add(createNumField(REC_READ));
        EXTENDED_FIELDS.add(createNumField(REC_WRITE));
        EXTENDED_FIELDS.add(createNumField(REC_INFO));
        EXTENDED_FIELDS.add(createNumField(REC_WARN));
        EXTENDED_FIELDS.add(createNumField(REC_ERROR));
        EXTENDED_FIELDS.add(createNumField(REC_FATAL));
        EXTENDED_FIELDS.add(createNumField(DURATION));
        EXTENDED_FIELDS.add(createNumField(FILE_SIZE));
        EXTENDED_FIELDS.add(createNumField(STREAM_SIZE));
    }

    private static DataSourceField createDateField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.BETWEEN)
                .addConditions(Condition.GREATER_THAN)
                .addConditions(Condition.GREATER_THAN_OR_EQUAL_TO)
                .addConditions(Condition.LESS_THAN)
                .addConditions(Condition.LESS_THAN_OR_EQUAL_TO)
                .type(DataSourceField.DataSourceFieldType.DATE_FIELD)
                .build();
    }

    private static DataSourceField createStringField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.FIELD)
                .build();
    }

    private static DataSourceField createIdField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.ID)
                .build();
    }

    private static DataSourceField createNumField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.BETWEEN)
                .addConditions(Condition.GREATER_THAN)
                .addConditions(Condition.GREATER_THAN_OR_EQUAL_TO)
                .addConditions(Condition.LESS_THAN)
                .addConditions(Condition.LESS_THAN_OR_EQUAL_TO)
                .type(DataSourceFieldType.NUMERIC_FIELD)
                .build();
    }
}

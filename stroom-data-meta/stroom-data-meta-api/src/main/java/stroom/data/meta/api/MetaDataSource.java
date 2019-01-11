package stroom.data.meta.api;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetaDataSource {
    public static final String STREAM_STORE_TYPE = "StreamStore";
    public static final DocRef STREAM_STORE_DOC_REF = new DocRef.Builder()
            .type(STREAM_STORE_TYPE)
            .uuid("0")
            .name(STREAM_STORE_TYPE)
            .build();

    private static final List<DataSourceField> FIELDS = new ArrayList<>();
    private static final Map<String, DataSourceField> FIELD_MAP;
    private static final List<DataSourceField> EXTENDED_FIELDS = new ArrayList<>();
    private static final Map<String, DataSourceField> EXTENDED_FIELD_MAP;
//    private static final Map<String, String> STREAM_FIELDS = new HashMap<>();
//    private static final Map<String, String> FEED_FIELDS = new HashMap<>();
//    private static final Map<String, String> STREAM_TYPE_FIELDS = new HashMap<>();
//    private static final Map<String, String> PIPELINE_FIELDS = new HashMap<>();

    // Non grouped fields
    public static final String FEED_NAME = "Feed";
    public static final String PIPELINE_UUID = "Pipeline";
    public static final String STATUS = "Status";
    public static final String STREAM_TYPE_NAME = "Type";

    // Id's
    public static final String STREAM_ID = "Id";
    public static final String PARENT_STREAM_ID = "Parent Id";
    public static final String STREAM_PROCESSOR_ID = "Stream Processor Id";

    // Times
    public static final String CREATE_TIME = "Create Time";
    public static final String EFFECTIVE_TIME = "Effective Time";
    public static final String STATUS_TIME = "Status Time";

    // Extended fields.
//    public static final String NODE = "Node";
    public static final String REC_READ = "Read Count";
    public static final String REC_WRITE = "Write Count";
    public static final String REC_INFO = "Info Count";
    public static final String REC_WARN = "Warning Count";
    public static final String REC_ERROR = "Error Count";
    public static final String REC_FATAL = "Fatal Error Count";
    public static final String DURATION = "Duration";
    public static final String FILE_SIZE = "File Size";
    public static final String STREAM_SIZE = "Raw Size";

    // Legacy or hidden fields.
    public static final String FEED_ID = "Feed Id";
    public static final String STREAM_TASK_ID = "Stream Task Id";

    static {
//        STREAM_FIELDS.put(STREAM_ID, StreamEntity.ID);
//        STREAM_FIELDS.put(PARENT_STREAM_ID, StreamEntity.PARENT_STREAM_ID);
//        STREAM_FIELDS.put(CREATE_TIME, StreamEntity.CREATE_MS);
//        FEED_FIELDS.put(FEED, FeedEntity.NAME);
//        STREAM_TYPE_FIELDS.put(STREAM_TYPE, StreamTypeEntity.NAME);
//        PIPELINE_FIELDS.put(PIPELINE, PipelineDoc.NAME);


        // Non grouped fields
        FIELDS.add(createDocRefField(FEED_NAME, "Feed"));
        FIELDS.add(createDocRefField(PIPELINE_UUID, "Pipeline"));
        FIELDS.add(createStringField(STATUS));
        FIELDS.add(createStringField(STREAM_TYPE_NAME));

        // Id's
        FIELDS.add(createIdField(STREAM_ID));
        FIELDS.add(createIdField(PARENT_STREAM_ID));
        FIELDS.add(createIdField(STREAM_PROCESSOR_ID));

        // Times
        FIELDS.add(createDateField(CREATE_TIME));
        FIELDS.add(createDateField(EFFECTIVE_TIME));
        FIELDS.add(createDateField(STATUS_TIME));

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));


        // Single Items
        EXTENDED_FIELDS.add(createDocRefField(FEED_NAME, "Feed"));
        EXTENDED_FIELDS.add(createNumField(DURATION));
        //        EXTENDED_FIELDS.add(createStringField(NODE));
        EXTENDED_FIELDS.add(createDocRefField(PIPELINE_UUID, "Pipeline"));
        EXTENDED_FIELDS.add(createStringField(STATUS));
        EXTENDED_FIELDS.add(createStringField(STREAM_TYPE_NAME));

        // Id's
        EXTENDED_FIELDS.add(createIdField(STREAM_ID));
        EXTENDED_FIELDS.add(createIdField(PARENT_STREAM_ID));
        EXTENDED_FIELDS.add(createIdField(STREAM_PROCESSOR_ID));

        // Counts
        EXTENDED_FIELDS.add(createNumField(REC_READ));
        EXTENDED_FIELDS.add(createNumField(REC_WRITE));
        EXTENDED_FIELDS.add(createNumField(REC_FATAL));
        EXTENDED_FIELDS.add(createNumField(REC_ERROR));
        EXTENDED_FIELDS.add(createNumField(REC_WARN));
        EXTENDED_FIELDS.add(createNumField(REC_INFO));

        // Times
        EXTENDED_FIELDS.add(createDateField(CREATE_TIME));
        EXTENDED_FIELDS.add(createDateField(EFFECTIVE_TIME));
        EXTENDED_FIELDS.add(createDateField(STATUS_TIME));

        // Sizes
        EXTENDED_FIELDS.add(createNumField(FILE_SIZE));
        EXTENDED_FIELDS.add(createNumField(STREAM_SIZE));
        EXTENDED_FIELD_MAP = EXTENDED_FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
    }

    private static DataSourceField createDateField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.BETWEEN)
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
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.IN)
                .addConditions(Condition.IN_DICTIONARY)
                .type(DataSourceField.DataSourceFieldType.FIELD)
                .build();
    }

    private static DataSourceField createIdField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.IN)
                .type(DataSourceField.DataSourceFieldType.ID)
                .build();
    }

    private static DataSourceField createDocRefField(final String name, final String docRefType) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(Condition.IS_DOC_REF)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.CONTAINS)
                .addConditions(Condition.IN)
                .addConditions(Condition.IN_DICTIONARY)
                .docRefType(docRefType)
                .build();
    }

    private static DataSourceField createNumField(final String name) {
        return new DataSourceField.Builder()
                .name(name)
                .addConditions(Condition.EQUALS)
                .addConditions(Condition.BETWEEN)
                .addConditions(Condition.GREATER_THAN)
                .addConditions(Condition.GREATER_THAN_OR_EQUAL_TO)
                .addConditions(Condition.LESS_THAN)
                .addConditions(Condition.LESS_THAN_OR_EQUAL_TO)
                .type(DataSourceFieldType.NUMERIC_FIELD)
                .build();
    }

    public static List<DataSourceField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, DataSourceField> getFieldMap() {
        return FIELD_MAP;
    }

    public static List<DataSourceField> getExtendedFields() {
        return new ArrayList<>(EXTENDED_FIELDS);
    }

    public static Map<String, DataSourceField> getExtendedFieldMap() {
        return EXTENDED_FIELD_MAP;
    }
}

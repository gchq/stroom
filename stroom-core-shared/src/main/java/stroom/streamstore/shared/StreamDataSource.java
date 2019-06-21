package stroom.streamstore.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StreamDataSource {
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
    private static final Map<String, String> STREAM_FIELDS = new HashMap<>();
    private static final Map<String, String> FEED_FIELDS = new HashMap<>();
    private static final Map<String, String> STREAM_TYPE_FIELDS = new HashMap<>();
    private static final Map<String, String> PIPELINE_FIELDS = new HashMap<>();

    public static final String FEED_NAME = "Feed";
    public static final String PIPELINE_UUID = "Pipeline";
    public static final String STREAM_TYPE_NAME = "Type";
    public static final String STREAM_ID = "Id";
    public static final String PARENT_STREAM_ID = "Parent Id";
    public static final String STATUS = "Status";
    public static final String CREATE_TIME = "Create Time";
    public static final String EFFECTIVE_TIME = "Effective Time";
    public static final String STATUS_TIME = "Status Time";

    // Extended fields.
    public static final String NODE = "Node";
    public static final String REC_READ = "Read Count";
    public static final String REC_WRITE = "Write Count";
    public static final String REC_INFO = "Info Count";
    public static final String REC_WARN = "Warning Count";
    public static final String REC_ERROR = "Error Count";
    public static final String REC_FATAL = "Fatal Error Count";
    public static final String DURATION = "Duration";
    public static final String FILE_SIZE = "File Size";
    public static final String STREAM_SIZE = "Raw Size";

    static {
        STREAM_FIELDS.put(STREAM_ID, Stream.ID);
        STREAM_FIELDS.put(PARENT_STREAM_ID, Stream.PARENT_STREAM_ID);
        STREAM_FIELDS.put(CREATE_TIME, Stream.CREATE_MS);
        FEED_FIELDS.put(FEED_NAME, Feed.NAME);
        STREAM_TYPE_FIELDS.put(STREAM_TYPE_NAME, StreamType.NAME);
        PIPELINE_FIELDS.put(PIPELINE_UUID, PipelineEntity.UUID);

        // Non grouped fields
        FIELDS.add(DataSourceUtil.createDocRefField(FEED_NAME, Feed.ENTITY_TYPE));
        FIELDS.add(DataSourceUtil.createDocRefField(PIPELINE_UUID, PipelineEntity.ENTITY_TYPE));
        FIELDS.add(DataSourceUtil.createStringField(STATUS));
        FIELDS.add(DataSourceUtil.createStringField(STREAM_TYPE_NAME));

        // Id's
        FIELDS.add(DataSourceUtil.createIdField(STREAM_ID));
        FIELDS.add(DataSourceUtil.createIdField(PARENT_STREAM_ID));

        // Times
        FIELDS.add(DataSourceUtil.createDateField(CREATE_TIME));
        FIELDS.add(DataSourceUtil.createDateField(EFFECTIVE_TIME));
        FIELDS.add(DataSourceUtil.createDateField(STATUS_TIME));
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));


        // Single Items
        EXTENDED_FIELDS.add(DataSourceUtil.createDocRefField(FEED_NAME, Feed.ENTITY_TYPE));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(DURATION));
        EXTENDED_FIELDS.add(DataSourceUtil.createStringField(NODE));
        EXTENDED_FIELDS.add(DataSourceUtil.createDocRefField(PIPELINE_UUID, PipelineEntity.ENTITY_TYPE));
        EXTENDED_FIELDS.add(DataSourceUtil.createStringField(STATUS));
        EXTENDED_FIELDS.add(DataSourceUtil.createStringField(STREAM_TYPE_NAME));

        // Id's
        EXTENDED_FIELDS.add(DataSourceUtil.createIdField(STREAM_ID));
        EXTENDED_FIELDS.add(DataSourceUtil.createIdField(PARENT_STREAM_ID));

        // Counts
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_READ));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_WRITE));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_FATAL));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_ERROR));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_WARN));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(REC_INFO));

        // Times
        EXTENDED_FIELDS.add(DataSourceUtil.createDateField(CREATE_TIME));
        EXTENDED_FIELDS.add(DataSourceUtil.createDateField(EFFECTIVE_TIME));
        EXTENDED_FIELDS.add(DataSourceUtil.createDateField(STATUS_TIME));

        // Sizes
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(FILE_SIZE));
        EXTENDED_FIELDS.add(DataSourceUtil.createNumField(STREAM_SIZE));
        EXTENDED_FIELD_MAP = EXTENDED_FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
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

    public static Map<String, String> getStreamFields() {
        return STREAM_FIELDS;
    }

    public static Map<String, String> getFeedFields() {
        return FEED_FIELDS;
    }

    public static Map<String, String> getStreamTypeFields() {
        return STREAM_TYPE_FIELDS;
    }

    public static Map<String, String> getPipelineFields() {
        return PIPELINE_FIELDS;
    }
}
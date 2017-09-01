package stroom.streamstore.server;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StreamFields {
    public static final String STREAM_ID = "Stream Id";
    public static final String PARENT_STREAM_ID = "Parent Stream Id";
    public static final String CREATED_ON = "Created On";
    public static final String FEED = "Feed";
    public static final String STREAM_TYPE = "Stream Type";
    public static final String PIPELINE = "Pipeline";

    private static final Map<String, String> STREAM_FIELDS = new HashMap<>();
    private static final Map<String, String> FEED_FIELDS = new HashMap<>();
    private static final Map<String, String> STREAM_TYPE_FIELDS = new HashMap<>();
    private static final Map<String, String> PIPELINE_FIELDS = new HashMap<>();
    //    private static final Map<String, String> STREAM_ATTRIBUTE_FIELDS = new HashMap<>();
    private static final List<DataSourceField> FIELDS;
    private static final Map<String, DataSourceField> INDEX_FIELD_MAP;

    static {
        STREAM_FIELDS.put(STREAM_ID, Stream.ID);
        STREAM_FIELDS.put(PARENT_STREAM_ID, Stream.PARENT_STREAM_ID);
        STREAM_FIELDS.put(CREATED_ON, Stream.CREATE_MS);

        FEED_FIELDS.put(FEED, Feed.NAME);

        STREAM_TYPE_FIELDS.put(STREAM_TYPE, StreamType.NAME);

        PIPELINE_FIELDS.put(PIPELINE, PipelineEntity.NAME);

        // TODO : Don't include these fields for now as the processing required to fetch attributes for each stream will be slow.
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_READ, StreamAttributeConstants.REC_READ);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_WRITE, StreamAttributeConstants.REC_WRITE);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_INFO, StreamAttributeConstants.REC_INFO);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_WARN, StreamAttributeConstants.REC_WARN);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_ERROR, StreamAttributeConstants.REC_ERROR);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_FATAL, StreamAttributeConstants.REC_FATAL);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.DURATION, StreamAttributeConstants.DURATION);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.FILE_SIZE, StreamAttributeConstants.FILE_SIZE);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.STREAM_SIZE, StreamAttributeConstants.STREAM_SIZE);

        final Set<DataSourceField> set = new HashSet<>();

        set.add(createField(DataSourceFieldType.NUMERIC_FIELD, STREAM_ID));
        set.add(createField(DataSourceFieldType.NUMERIC_FIELD, PARENT_STREAM_ID));
        set.add(createField(DataSourceFieldType.DATE_FIELD, CREATED_ON));

        set.add(createField(DataSourceFieldType.FIELD, FEED));
        set.add(createField(DataSourceFieldType.FIELD, STREAM_TYPE));
        set.add(createField(DataSourceFieldType.FIELD, PIPELINE));

//        STREAM_ATTRIBUTE_FIELDS.forEach((k, v) -> {
//            final IndexField indexField = create(k, StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.get(v));
//            if (indexField != null) {
//                set.add(indexField);
//            }
//        });

        final List<DataSourceField> list = set.stream().sorted(Comparator.comparing(DataSourceField::getName)).collect(Collectors.toList());
        FIELDS = list;
        INDEX_FIELD_MAP = list.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
    }

    private static DataSourceField createField(final DataSourceFieldType fieldType, final String fieldName) {
        return new DataSourceField(fieldType, fieldName, true, getDefaultConditions(fieldType));
    }

    private static List<Condition> getDefaultConditions(final DataSourceFieldType fieldType) {
        final List<Condition> conditions = new ArrayList<>();

        if (fieldType != null) {
            // First make sure the operator is set.
            switch (fieldType) {
                case ID:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;
                case FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;

                case NUMERIC_FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.GREATER_THAN);
                    conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.LESS_THAN);
                    conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.BETWEEN);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;

                case DATE_FIELD:
                    conditions.add(Condition.EQUALS);
                    conditions.add(Condition.GREATER_THAN);
                    conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.LESS_THAN);
                    conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
                    conditions.add(Condition.BETWEEN);
                    conditions.add(Condition.IN);
                    conditions.add(Condition.IN_DICTIONARY);
                    break;
            }
        }

        return conditions;
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

    public static List<DataSourceField> getFields() {
        return FIELDS;
    }

    public static Map<String, DataSourceField> getFieldMap() {
        return INDEX_FIELD_MAP;
    }
}

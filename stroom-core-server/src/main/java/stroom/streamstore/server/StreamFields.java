package stroom.streamstore.server;

import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;

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
    private static final IndexFields FIELDS;
    private static final Map<String, IndexField> INDEX_FIELD_MAP;

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

        final Set<IndexField> set = new HashSet<>();

        set.add(IndexField.createNumericField(STREAM_ID));
        set.add(IndexField.createNumericField(PARENT_STREAM_ID));
        set.add(IndexField.createDateField(CREATED_ON));

        set.add(IndexField.createField(FEED));
        set.add(IndexField.createField(STREAM_TYPE));
        set.add(IndexField.createField(PIPELINE));

//        STREAM_ATTRIBUTE_FIELDS.forEach((k, v) -> {
//            final IndexField indexField = create(k, StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.get(v));
//            if (indexField != null) {
//                set.add(indexField);
//            }
//        });

        final List<IndexField> list = set.stream().sorted(Comparator.comparing(IndexField::getFieldName)).collect(Collectors.toList());
        FIELDS = new IndexFields(list);
        INDEX_FIELD_MAP = list.stream().collect(Collectors.toMap(IndexField::getFieldName, Function.identity()));
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

    public static IndexFields getFields() {
        return FIELDS;
    }

    public static Map<String, IndexField> getFieldMap() {
        return INDEX_FIELD_MAP;
    }
}

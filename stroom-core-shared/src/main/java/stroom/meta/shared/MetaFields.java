package stroom.meta.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetaFields {
    public static final String STREAM_STORE_TYPE = "StreamStore";
    public static final DocRef STREAM_STORE_DOC_REF = new DocRef.Builder()
            .type(STREAM_STORE_TYPE)
            .uuid("0")
            .name(STREAM_STORE_TYPE)
            .build();

    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;
    private static final List<AbstractField> EXTENDED_FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> EXTENDED_FIELD_MAP;
//    private static final Map<String, String> STREAM_FIELDS = new HashMap<>();
//    private static final Map<String, String> FEED_FIELDS = new HashMap<>();
//    private static final Map<String, String> STREAM_TYPE_FIELDS = new HashMap<>();
//    private static final Map<String, String> PIPELINE_FIELDS = new HashMap<>();

    // Non grouped fields
    public static final TextField FEED_NAME = new TextField("Feed Name");
    public static final DocRefField FEED = new DocRefField("Feed", "Feed");
    public static final DocRefField PIPELINE = new DocRefField("Pipeline", "Pipeline");
    public static final TextField STATUS = new TextField("Status");
    public static final TextField TYPE_NAME = new TextField("Type");

    // Id's
    public static final IdField ID = new IdField("Id");
    public static final IdField PARENT_ID = new IdField("Parent Id");
    public static final IdField PROCESSOR_ID = new IdField("Processor Id");

    // Times
    public static final DateField CREATE_TIME = new DateField("Create Time");
    public static final DateField EFFECTIVE_TIME = new DateField("Effective Time");
    public static final DateField STATUS_TIME = new DateField("Status Time");

    // Extended fields.
//    public static final String NODE = "Node";
    public static final LongField REC_READ = new LongField("Read Count");
    public static final LongField REC_WRITE = new LongField("Write Count");
    public static final LongField REC_INFO = new LongField("Info Count");
    public static final LongField REC_WARN = new LongField("Warning Count");
    public static final LongField REC_ERROR = new LongField("Error Count");
    public static final LongField REC_FATAL = new LongField("Fatal Error Count");
    public static final LongField DURATION = new LongField("Duration");
    public static final LongField FILE_SIZE = new LongField("File Size");
    public static final LongField RAW_SIZE = new LongField("Raw Size");

    // Legacy or hidden fields.
    public static final IdField FEED_ID = new IdField("Feed Id");
    public static final IdField TASK_ID = new IdField("Task Id");

    static {
//        STREAM_FIELDS.put(ID, StreamEntity.ID);
//        STREAM_FIELDS.put(PARENT_ID, StreamEntity.PARENT_ID);
//        STREAM_FIELDS.put(CREATE_TIME, StreamEntity.CREATE_MS);
//        FEED_FIELDS.put(FEED, FeedEntity.NAME);
//        STREAM_TYPE_FIELDS.put(STREAM_TYPE, StreamTypeEntity.NAME);
//        PIPELINE_FIELDS.put(PIPELINE, PipelineDoc.NAME);


        // Non grouped fields
        FIELDS.add(FEED_NAME);
        FIELDS.add(FEED);
        FIELDS.add(PIPELINE);
        FIELDS.add(STATUS);
        FIELDS.add(TYPE_NAME);

        // Id's
        FIELDS.add(ID);
        FIELDS.add(PARENT_ID);
        FIELDS.add(PROCESSOR_ID);

        // Times
        FIELDS.add(CREATE_TIME);
        FIELDS.add(EFFECTIVE_TIME);
        FIELDS.add(STATUS_TIME);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));

        // Single Items
        EXTENDED_FIELDS.add(FEED_NAME);
        EXTENDED_FIELDS.add(FEED);
        EXTENDED_FIELDS.add(DURATION);
        //        EXTENDED_FIELDS.add(createStringField(NODE));
        EXTENDED_FIELDS.add(PIPELINE);
        EXTENDED_FIELDS.add(STATUS);
        EXTENDED_FIELDS.add(TYPE_NAME);

        // Id's
        EXTENDED_FIELDS.add(ID);
        EXTENDED_FIELDS.add(PARENT_ID);
        EXTENDED_FIELDS.add(PROCESSOR_ID);

        // Counts
        EXTENDED_FIELDS.add(REC_READ);
        EXTENDED_FIELDS.add(REC_WRITE);
        EXTENDED_FIELDS.add(REC_FATAL);
        EXTENDED_FIELDS.add(REC_ERROR);
        EXTENDED_FIELDS.add(REC_WARN);
        EXTENDED_FIELDS.add(REC_INFO);

        // Times
        EXTENDED_FIELDS.add(CREATE_TIME);
        EXTENDED_FIELDS.add(EFFECTIVE_TIME);
        EXTENDED_FIELDS.add(STATUS_TIME);

        // Sizes
        EXTENDED_FIELDS.add(FILE_SIZE);
        EXTENDED_FIELDS.add(RAW_SIZE);
        EXTENDED_FIELD_MAP = EXTENDED_FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }

    public static List<AbstractField> getExtendedFields() {
        return new ArrayList<>(EXTENDED_FIELDS);
    }

    public static Map<String, AbstractField> getExtendedFieldMap() {
        return EXTENDED_FIELD_MAP;
    }
}

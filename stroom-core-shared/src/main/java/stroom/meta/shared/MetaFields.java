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

    public static final String FIELD_ID = "Id";
    public static final String FIELD_FEED = "Feed";
    public static final String FIELD_TYPE = "Type";
    public static final String FIELD_PARENT_FEED = "Parent Feed";

    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;
    private static final List<AbstractField> EXTENDED_FIELDS = new ArrayList<>();
    private static final List<AbstractField> ALL_FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> ALL_FIELD_MAP;

    // Non grouped fields
    public static final TextField FEED_NAME = new TextField("Feed Name");
    public static final DocRefField FEED = new DocRefField("Feed", "Feed");
    public static final DocRefField PIPELINE = new DocRefField("Pipeline", "Pipeline");
    public static final TextField STATUS = new TextField("Status");
    public static final TextField TYPE_NAME = new TextField("Type");

    // Id's
    public static final IdField ID = new IdField("Id");
    public static final IdField META_INTERNAL_PROCESSOR_ID = new IdField("Processor Id");

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

    // Parent fields.
    public static final IdField PARENT_ID = new IdField("Parent Id");
    public static final TextField PARENT_STATUS = new TextField("Parent Status");
    public static final DateField PARENT_CREATE_TIME = new DateField("Parent Create Time");
    public static final DocRefField PARENT_FEED = new DocRefField("Feed", FIELD_PARENT_FEED);

    static {
        // Non grouped fields
        FIELDS.add(FEED_NAME);
        FIELDS.add(FEED);
        FIELDS.add(PIPELINE);
        FIELDS.add(STATUS);
        FIELDS.add(TYPE_NAME);

        // Id's
        FIELDS.add(ID);
        FIELDS.add(PARENT_ID);
        FIELDS.add(META_INTERNAL_PROCESSOR_ID);

        // Times
        FIELDS.add(CREATE_TIME);
        FIELDS.add(EFFECTIVE_TIME);
        FIELDS.add(STATUS_TIME);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));

        // Single Items
        EXTENDED_FIELDS.add(DURATION);

        // Counts
        EXTENDED_FIELDS.add(REC_READ);
        EXTENDED_FIELDS.add(REC_WRITE);
        EXTENDED_FIELDS.add(REC_FATAL);
        EXTENDED_FIELDS.add(REC_ERROR);
        EXTENDED_FIELDS.add(REC_WARN);
        EXTENDED_FIELDS.add(REC_INFO);

        // Sizes
        EXTENDED_FIELDS.add(FILE_SIZE);
        EXTENDED_FIELDS.add(RAW_SIZE);

        ALL_FIELDS.addAll(FIELDS);
        ALL_FIELDS.addAll(EXTENDED_FIELDS);
        ALL_FIELD_MAP = ALL_FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }

    public static List<AbstractField> getAllFields() {
        return ALL_FIELDS;
    }

    public static Map<String, AbstractField> getAllFieldMap() {
        return ALL_FIELD_MAP;
    }

    public static List<AbstractField> getExtendedFields() {
        return EXTENDED_FIELDS;
    }
}

package stroom.processor.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorTaskFields {

    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    public static final String FIELD_ID = "Id";
    public static final String FIELD_CREATE_TIME = "Created";
    public static final String FIELD_START_TIME = "Start Time";
    public static final String FIELD_END_TIME_DATE = "End Time";
    public static final String FIELD_FEED = "Feed";
    public static final String FIELD_PRIORITY = "Priority";
    public static final String FIELD_PIPELINE = "Pipeline";
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_COUNT = "Count";
    public static final String FIELD_NODE = "Node";
    public static final String FIELD_POLL_AGE = "Poll Age";

    public static final DateField CREATE_TIME = new DateField("Create Time");
    public static final LongField CREATE_TIME_MS = new LongField("Create Time Ms");
    public static final DateField START_TIME = new DateField("Start Time");
    public static final LongField START_TIME_MS = new LongField("Start Time Ms");
    public static final LongField END_TIME_MS = new LongField("End Time Ms");
    public static final DateField END_TIME = new DateField("End Time");
    public static final LongField STATUS_TIME_MS = new LongField("Status Time Ms");
    public static final DateField STATUS_TIME = new DateField("Status Time");
    public static final IdField META_ID = new IdField("Meta Id");
    public static final TextField NODE_NAME = new TextField("Node");
    public static final DocRefField PIPELINE = new DocRefField(PipelineDoc.DOCUMENT_TYPE, "Pipeline");
    public static final IdField PROCESSOR_FILTER_ID = new IdField("Processor Filter Id");
    public static final LongField PROCESSOR_FILTER_PRIORITY = new LongField("Processor Filter Priority");
    public static final IdField PROCESSOR_ID = new IdField("Processor Id");
    public static final DocRefField FEED = new DocRefField("Feed", "Feed");
    public static final TextField STATUS = new TextField("Status");
    public static final IdField TASK_ID = new IdField("Task Id");

    static {
        FIELDS.add(CREATE_TIME);
        FIELDS.add(CREATE_TIME_MS);
        FIELDS.add(START_TIME);
        FIELDS.add(START_TIME_MS);
        FIELDS.add(END_TIME);
        FIELDS.add(END_TIME_MS);
        FIELDS.add(STATUS_TIME);
        FIELDS.add(STATUS_TIME_MS);
        FIELDS.add(META_ID);
        FIELDS.add(NODE_NAME);
        FIELDS.add(PIPELINE);
        FIELDS.add(PROCESSOR_FILTER_ID);
        FIELDS.add(PROCESSOR_FILTER_PRIORITY);
        FIELDS.add(PROCESSOR_ID);
        FIELDS.add(FEED);
        FIELDS.add(STATUS);
        FIELDS.add(TASK_ID);
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}

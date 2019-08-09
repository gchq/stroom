package stroom.processor.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorFilterDataSource {
    public static final String FIELD_ID = "Id";

    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    public static final TextField CREATE_USER = new TextField("Create User");
    public static final LongField LAST_POLL_MS = new LongField("Last Poll Ms");
    public static final DocRefField PIPELINE = new DocRefField(PipelineDoc.DOCUMENT_TYPE, "Pipeline");
//    public static final TextField PIPELINE_UUID = new TextField("Pipeline UUID");
    public static final IntegerField PRIORITY = new IntegerField("Priority");
    public static final BooleanField PROCESSOR_ENABLED = new BooleanField("Processor Enabled");
    public static final BooleanField PROCESSOR_FILTER_ENABLED = new BooleanField("Processor Filter Enabled");
    public static final IdField PROCESSOR_ID = new IdField("Processor Id");

    static {
        FIELDS.add(CREATE_USER);
        FIELDS.add(LAST_POLL_MS);
        FIELDS.add(PIPELINE);
//        FIELDS.add(PIPELINE_UUID);
        FIELDS.add(PRIORITY);
        FIELDS.add(PROCESSOR_ENABLED);
        FIELDS.add(PROCESSOR_FILTER_ENABLED);
        FIELDS.add(PROCESSOR_ID);
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}
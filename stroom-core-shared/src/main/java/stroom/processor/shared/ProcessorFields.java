package stroom.processor.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorFields {
    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    public static final IdField ID = new IdField("Processor Id");
    public static final TextField CREATE_USER = new TextField("Processor Create User");
    public static final DocRefField PIPELINE = new DocRefField(PipelineDoc.DOCUMENT_TYPE, "Processor Pipeline");
    public static final BooleanField ENABLED = new BooleanField("Processor Enabled");
    public static final BooleanField DELETED = new BooleanField("Processor Deleted");
    public static final TextField UUID = new TextField("Processor UUID");

    static {
        FIELDS.add(ID);
        FIELDS.add(CREATE_USER);
        FIELDS.add(PIPELINE);
        FIELDS.add(ENABLED);
        FIELDS.add(DELETED);
        FIELDS.add(UUID);
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }
}
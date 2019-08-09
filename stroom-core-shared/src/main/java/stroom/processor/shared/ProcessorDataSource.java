package stroom.processor.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorDataSource {
    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    public static final TextField CREATE_USER = new TextField("Create User");
    public static final DocRefField PIPELINE = new DocRefField(PipelineDoc.DOCUMENT_TYPE, "Pipeline");

    static {
        FIELDS.add(PIPELINE);
        FIELDS.add(CREATE_USER);
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}
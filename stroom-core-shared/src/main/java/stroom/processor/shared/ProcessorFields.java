package stroom.processor.shared;

import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

public class ProcessorFields {

    public static final IdField ID = new IdField("Processor Id");
    public static final TextField CREATE_USER = new TextField("Processor Create User");
    public static final DocRefField PIPELINE = DocRefField.byUuid(
            PipelineDoc.DOCUMENT_TYPE, "Processor Pipeline");
    public static final BooleanField ENABLED = new BooleanField("Processor Enabled");
    public static final BooleanField DELETED = new BooleanField("Processor Deleted");
    public static final TextField UUID = new TextField("Processor UUID");
}

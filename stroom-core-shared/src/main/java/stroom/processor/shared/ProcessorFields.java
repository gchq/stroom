package stroom.processor.shared;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.TextField;
import stroom.pipeline.shared.PipelineDoc;

public class ProcessorFields {

    public static final IdField ID = new IdField("Processor Id");
    public static final TextField PROCESSOR_TYPE = new TextField("Processor Type");
    public static final DocRefField PIPELINE = DocRefField.byUuid(
            PipelineDoc.DOCUMENT_TYPE, "Processor Pipeline");
    public static final DocRefField ANALYTIC_RULE = DocRefField.byUuid(
            AnalyticRuleDoc.DOCUMENT_TYPE, "Analytic Rule");
    public static final BooleanField ENABLED = new BooleanField("Processor Enabled");
    public static final BooleanField DELETED = new BooleanField("Processor Deleted");
    public static final TextField UUID = new TextField("Processor UUID");
}

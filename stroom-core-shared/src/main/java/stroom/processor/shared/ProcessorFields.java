package stroom.processor.shared;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.datasource.api.v2.QueryField;
import stroom.pipeline.shared.PipelineDoc;

public class ProcessorFields {

    public static final QueryField ID = QueryField.createId("Processor Id");
    public static final QueryField PROCESSOR_TYPE = QueryField.createText("Processor Type");
    public static final QueryField PIPELINE = QueryField.byUuid(
            PipelineDoc.DOCUMENT_TYPE, "Processor Pipeline");
    public static final QueryField ANALYTIC_RULE = QueryField.byUuid(
            AnalyticRuleDoc.DOCUMENT_TYPE, "Analytic Rule");
    public static final QueryField ENABLED = QueryField.createBoolean("Processor Enabled");
    public static final QueryField DELETED = QueryField.createBoolean("Processor Deleted");
    public static final QueryField UUID = QueryField.createText("Processor UUID");
}

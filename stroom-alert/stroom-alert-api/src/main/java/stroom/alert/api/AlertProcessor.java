package stroom.alert.api;

import stroom.index.shared.IndexDoc;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;

import java.util.Map;

public interface AlertProcessor {
    void createAlerts(final Document document, final IndexDoc index);
    void setFieldAnalyzers (final Map<String, Analyzer> analyzerMap);
}

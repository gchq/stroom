package stroom.alert.api;

import org.apache.lucene.document.Document;

public interface AlertProcessor {
    void createAlerts(final Document document);
}

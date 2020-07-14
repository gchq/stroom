package stroom.alert.api;

import org.apache.lucene.document.Document;

public interface AlertProcessor {
    void addIfNeeded(final Document document);
    void createAlerts();
}


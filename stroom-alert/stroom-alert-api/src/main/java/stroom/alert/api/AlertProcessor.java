package stroom.alert.api;

import stroom.docref.DocRef;

import org.apache.lucene.document.Document;

public interface AlertProcessor {
    void addIfNeeded(final Document document);
    void createAlerts();
}

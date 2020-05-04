package stroom.alert.api;

import org.apache.lucene.document.Document;

public interface AlertManager {

    void createAlerts(final Document document);
}

package stroom.alert.impl;

import org.apache.lucene.document.Document;
import stroom.alert.api.AlertManager;

import org.apache.lucene.index.memory.MemoryIndex;

public class AlertManagerImpl implements AlertManager {
    @Override
    public void createAlerts(Document document) {
        System.out.println("Alerting " + document);
    }

    public void loadRules(){

    }
}

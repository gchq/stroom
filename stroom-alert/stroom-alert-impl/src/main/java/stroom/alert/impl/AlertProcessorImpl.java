package stroom.alert.impl;

import org.apache.lucene.document.Document;
import stroom.alert.api.AlertProcessor;
import stroom.docref.DocRef;

public class AlertProcessorImpl implements AlertProcessor {

    private final DocRef rulesFolder;


    public AlertProcessorImpl (final DocRef rulesFolder){
        this.rulesFolder = rulesFolder;
        System.out.println("Creating AlertProcessorImpl");
    }

    @Override
    public void createAlerts(Document document) {
        System.out.println("Alerting " + document + " with rules from " + rulesFolder);
    }

    public void loadRules(){

    }
}

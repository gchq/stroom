package stroom.alert.impl;

import org.apache.lucene.document.Document;
import stroom.alert.api.AlertManager;

import org.apache.lucene.index.memory.MemoryIndex;
import stroom.alert.api.AlertProcessor;
import stroom.docref.DocRef;

import javax.inject.Singleton;

@Singleton
public class AlertManagerImpl implements AlertManager {

    @Override
    public AlertProcessor createAlertProcessor(DocRef rulesFolderRef) {
        return new AlertProcessorImpl(rulesFolderRef);
    }
}

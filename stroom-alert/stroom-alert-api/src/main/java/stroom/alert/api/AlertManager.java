package stroom.alert.api;

import org.apache.lucene.document.Document;
import stroom.docref.DocRef;

public interface AlertManager {
    AlertProcessor createAlertProcessor (DocRef rulesFolderRef);
}

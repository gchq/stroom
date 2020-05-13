package stroom.alert.api;

import org.apache.lucene.document.Document;
import stroom.docref.DocRef;

import java.util.List;

public interface AlertManager {
    AlertProcessor createAlertProcessor (final DocRef indexDocRef);
}

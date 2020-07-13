package stroom.alert.api;

import org.apache.lucene.document.Document;
import stroom.docref.DocRef;

import java.util.List;
import java.util.Optional;

public interface AlertManager {
    Optional<AlertProcessor> createAlertProcessor (final DocRef indexDocRef, final long streamId);
}

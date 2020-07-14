package stroom.alert.api;

import stroom.docref.DocRef;

import java.util.Optional;

public interface AlertManager {
    Optional<AlertProcessor> createAlertProcessor(final DocRef indexDocRef);
}

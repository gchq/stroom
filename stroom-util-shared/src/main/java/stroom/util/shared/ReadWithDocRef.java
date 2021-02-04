package stroom.util.shared;

import stroom.docref.DocRef;

public interface ReadWithDocRef <T> {
    T read(DocRef docRef);
}

package stroom.docrefinfo.api;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;

import java.util.Optional;

public interface DocRefInfoService {
    Optional<DocRefInfo> info(DocRef docRef);

    Optional<String> name(DocRef docRef);
}

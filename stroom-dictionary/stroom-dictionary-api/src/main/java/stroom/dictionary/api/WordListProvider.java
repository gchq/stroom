package stroom.dictionary.api;

import stroom.docref.DocRef;

import java.util.List;
import java.util.Optional;

public interface WordListProvider {

    String getCombinedData(DocRef dictionaryRef);

    String[] getWords(DocRef dictionaryRef);

    List<DocRef> findByName(String name);

    Optional<DocRef> findByUuid(String uuid);
}

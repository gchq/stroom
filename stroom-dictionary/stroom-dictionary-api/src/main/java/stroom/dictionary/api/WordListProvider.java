package stroom.dictionary.api;

import stroom.docref.DocRef;

import java.util.List;

public interface WordListProvider {
    List<DocRef> findByName(String dictionaryName);

    String getCombinedData(DocRef dictionaryRef);

    String[] getWords(DocRef dictionaryRef);
}

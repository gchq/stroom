package stroom.streamstore.server;

import stroom.query.api.v2.DocRef;

import java.util.List;

public interface WordListProvider {
    List<DocRef> findByName(String dictionaryName);

    String getCombinedData(DocRef dictionaryRef);

    String[] getWords(DocRef dictionaryRef);
}

package stroom.dictionary.api;

import stroom.docref.DocRef;
import stroom.docref.HasFindDocsByName;

public interface WordListProvider extends HasFindDocsByName {

    String getCombinedData(DocRef dictionaryRef);

    String[] getWords(DocRef dictionaryRef);
}

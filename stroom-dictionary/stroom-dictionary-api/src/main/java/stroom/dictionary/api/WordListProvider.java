package stroom.dictionary.api;

import stroom.docref.DocRef;
import stroom.docref.HasFindDocRefsByName;

public interface WordListProvider extends HasFindDocRefsByName {

    String getCombinedData(DocRef dictionaryRef);

    String[] getWords(DocRef dictionaryRef);
}

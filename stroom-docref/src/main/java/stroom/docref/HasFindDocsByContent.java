package stroom.docref;

import java.util.List;

public interface HasFindDocsByContent {

    List<DocContentMatch> findByContent(StringMatch filter);

    DocContentHighlights fetchHighlights(DocRef docRef, String extension, StringMatch filter);
}

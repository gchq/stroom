package stroom.docref;

import java.util.List;

public interface HasFindDocsByContent {

    List<DocContentMatch> findByContent(String pattern, boolean regex, boolean matchCase);
}

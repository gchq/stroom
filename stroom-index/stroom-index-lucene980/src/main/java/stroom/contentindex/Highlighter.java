package stroom.contentindex;

import stroom.docref.StringMatchLocation;

import org.apache.lucene980.index.IndexReader;

import java.io.IOException;
import java.util.List;

public interface Highlighter {

    List<StringMatchLocation> getHighlights(IndexReader indexReader,
                                            int docId,
                                            String text,
                                            int maxMatches) throws IOException;

    boolean filter(String text);
}

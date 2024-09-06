package stroom.contentindex;

import stroom.docref.StringMatchLocation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene980.index.IndexReader;
import org.apache.lucene980.search.Query;
import org.apache.lucene980.search.vectorhighlight.BaseFragListBuilder;
import org.apache.lucene980.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene980.search.vectorhighlight.FieldFragList;
import org.apache.lucene980.search.vectorhighlight.FieldPhraseList;
import org.apache.lucene980.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo;
import org.apache.lucene980.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo.Toffs;
import org.apache.lucene980.search.vectorhighlight.FieldQuery;
import org.apache.lucene980.search.vectorhighlight.SimpleFieldFragList;
import org.apache.lucene980.search.vectorhighlight.SimpleFragmentsBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LuceneContentHighlighter implements Highlighter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentHighlighter.class);

    private final String field;
    private final Query query;

    public LuceneContentHighlighter(final String field, final Query query) {
        this.field = field;
        this.query = query;
    }

    @Override
    public List<StringMatchLocation> getHighlights(final IndexReader indexReader,
                                                   final int docId,
                                                   final String text,
                                                   final int maxMatches) throws IOException {
        final List<StringMatchLocation> list = new ArrayList<>();
        try {
            final BaseFragListBuilder fragListBuilder = new BaseFragListBuilder() {
                @Override
                public FieldFragList createFieldFragList(final FieldPhraseList fieldPhraseList,
                                                         final int fragCharSize) {
                    for (final WeightedPhraseInfo wpi : fieldPhraseList.getPhraseList()) {
                        for (final Toffs toffs : wpi.getTermsOffsets()) {
                            final StringMatchLocation stringMatchLocation = new StringMatchLocation(
                                    toffs.getStartOffset(),
                                    toffs.getEndOffset() - toffs.getStartOffset());
                            list.add(stringMatchLocation);
                            if (list.size() >= maxMatches) {
                                throw new ListFullException();
                            }
                        }
                    }
                    return new SimpleFieldFragList(fragCharSize);
                }

                @Override
                protected FieldFragList createFieldFragList(final FieldPhraseList fieldPhraseList,
                                                            final FieldFragList fieldFragList,
                                                            final int fragCharSize) {
                    for (final WeightedPhraseInfo wpi : fieldPhraseList.getPhraseList()) {
                        for (final Toffs toffs : wpi.getTermsOffsets()) {
                            final StringMatchLocation stringMatchLocation = new StringMatchLocation(
                                    toffs.getStartOffset(),
                                    toffs.getEndOffset() - toffs.getStartOffset());
                            list.add(stringMatchLocation);
                            if (list.size() >= maxMatches) {
                                throw new ListFullException();
                            }
                        }
                    }
                    return fieldFragList;
                }
            };


            final FastVectorHighlighter fastVectorHighlighter = new FastVectorHighlighter(
                    true,
                    true,
                    fragListBuilder,
                    new SimpleFragmentsBuilder());
            final FieldQuery fieldQuery = fastVectorHighlighter.getFieldQuery(
                    query,
                    indexReader);
            fastVectorHighlighter.getBestFragment(fieldQuery,
                    indexReader,
                    docId,
                    field,
                    18);
        } catch (ListFullException e) {
            // Ignore.
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

    @Override
    public boolean filter(final String text) {
        return true;
    }

    private static class ListFullException extends RuntimeException {

    }
}

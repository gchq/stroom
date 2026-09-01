/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.contentindex;

import stroom.explorer.shared.StringMatchLocation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.vectorhighlight.BaseFragListBuilder;
import org.apache.lucene.search.vectorhighlight.FastVectorHighlighter;
import org.apache.lucene.search.vectorhighlight.FieldFragList;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo;
import org.apache.lucene.search.vectorhighlight.FieldPhraseList.WeightedPhraseInfo.Toffs;
import org.apache.lucene.search.vectorhighlight.FieldQuery;
import org.apache.lucene.search.vectorhighlight.SimpleFieldFragList;
import org.apache.lucene.search.vectorhighlight.SimpleFragmentsBuilder;

import java.util.ArrayList;
import java.util.List;

public class LuceneContentHighlighter implements ContentHighlighter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentHighlighter.class);

    private final IndexReader indexReader;
    private final int docId;
    private final String field;
    private final Query query;

    public LuceneContentHighlighter(final IndexReader indexReader,
                                    final int docId,
                                    final String field,
                                    final Query query) {
        this.indexReader = indexReader;
        this.docId = docId;
        this.field = field;
        this.query = query;
    }

    @Override
    public List<StringMatchLocation> getHighlights(final String text,
                                                   final int maxMatches) {
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
        } catch (final ListFullException e) {
            // Ignore.
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }


    // --------------------------------------------------------------------------------


    private static class ListFullException extends RuntimeException {

    }
}

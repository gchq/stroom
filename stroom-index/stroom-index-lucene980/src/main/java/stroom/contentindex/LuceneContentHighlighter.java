package stroom.contentindex;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.docref.StringMatchLocation;
import stroom.index.lucene980.analyser.AnalyzerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.lucene980.analysis.Analyzer;
import org.apache.lucene980.analysis.TokenStream;
import org.apache.lucene980.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene980.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene980.search.Query;
import org.apache.lucene980.search.highlight.Formatter;
import org.apache.lucene980.search.highlight.Fragmenter;
import org.apache.lucene980.search.highlight.Highlighter;
import org.apache.lucene980.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene980.search.highlight.QueryScorer;
import org.apache.lucene980.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene980.search.highlight.SimpleSpanFragmenter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LuceneContentHighlighter implements ContentHighlighter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentHighlighter.class);

    public static final int DEFAULT_MAX_CHARS_TO_ANALYZE = 50 * 1024;

    private final int maxDocCharsToAnalyze = DEFAULT_MAX_CHARS_TO_ANALYZE;

    private final Query query;

    // Uses HTML &lt;B&gt;&lt;/B&gt; tag to highlight the searched terms
    private final Formatter formatter;

    // It scores text fragments by the number of unique query terms found
    // Basically the matching score in layman terms
    private final QueryScorer scorer;

    // Used to markup highlighted terms found in the best sections of a text
    private final Highlighter highlighter;

    // It breaks text up into same-size texts but does not split up spans
    private final Fragmenter fragmenter;
    private final Analyzer analyzer;

    public LuceneContentHighlighter(final Query query, final boolean caseSensitive) {
        this.query = query;


        // Uses HTML &lt;B&gt;&lt;/B&gt; tag to highlight the searched terms
        formatter = new SimpleHTMLFormatter();

        // It scores text fragments by the number of unique query terms found
        // Basically the matching score in layman terms
        scorer = new QueryScorer(query);

        // Used to markup highlighted terms found in the best sections of a text
        highlighter = new Highlighter(formatter, scorer);

        // It breaks text up into same-size texts but does not split up spans
        fragmenter = new SimpleSpanFragmenter(scorer, 10);

        // Breaks text up into same-size fragments with no concerns over spotting sentence boundaries.
        // Fragmenter fragmenter = new SimpleFragmenter(10);

        //set fragmenter to highlighter
        highlighter.setTextFragmenter(fragmenter);

        // Create token stream
        analyzer = AnalyzerFactory.create(AnalyzerType.KEYWORD, caseSensitive);
    }

    @Override
    public List<StringMatchLocation> getHighlights(final String field, final String text, final int maxMatches) {
        try {

//            final TokenStream stream = TokenSources.getAnyTokenStream(directoryReader,
//                    docId,
//                    DATA,
//                    analyzer);


            final TokenStream tokenStream = analyzer.tokenStream(field, text);
            return getBestTextFragments2(
                    tokenStream,
                    text,
                    scorer,
                    fragmenter,
                    100);

//            //Get highlighted text fragments
//            final String[] frags = highlighter.getBestFragments(stream, text, 10);
//            for (final String frag : frags) {
//                System.out.println("=======================");
//                System.out.println(frag);
//            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Low level api to get the most relevant (formatted) sections of the document. This method has
     * been made public to allow visibility of score information held in TextFragment objects. Thanks
     * to Jason Calabrese for help in redefining the interface.
     *
     * @throws IOException                  If there is a low-level I/O error
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided
     *                                      text's length
     */
    public final List<StringMatchLocation> getBestTextFragments2(
            TokenStream tokenStream,
            String text,
            final QueryScorer fragmentScorer,
            final Fragmenter textFragmenter,
            final int maxMatches)
            throws IOException, InvalidTokenOffsetsException {
        final List<StringMatchLocation> matchLocations = new ArrayList<>(maxMatches);

//        ArrayList<TextFragImpl> docFrags = new ArrayList<>();
        StringBuilder newText = new StringBuilder();

        int fragCount = 0;
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        TextFragImpl currentFrag = new TextFragImpl(newText, newText.length(), fragCount);
        fragmentScorer.setMaxDocCharsToAnalyze(maxDocCharsToAnalyze);
        TokenStream newStream = fragmentScorer.init(tokenStream);
        if (newStream != null) {
            tokenStream = newStream;
        }
        fragmentScorer.startFragment(currentFrag);
        fragCount++;
//        docFrags.add(currentFrag);

//        List<TextFragment> fragQueue = new ArrayList<>(maxNumFragments);

        try {

//            String tokenText;
            int startOffset;
            int endOffset;
//            int lastEndOffset = 0;
            textFragmenter.start(text, tokenStream);

            TokenGroupImpl tokenGroup = new TokenGroupImpl(tokenStream);

            tokenStream.reset();
            for (boolean next = tokenStream.incrementToken();
                 next && (offsetAtt.startOffset() < maxDocCharsToAnalyze);
                 next = tokenStream.incrementToken()) {
                if ((offsetAtt.endOffset() > text.length()) || (offsetAtt.startOffset() > text.length())) {
                    throw new InvalidTokenOffsetsException(
                            "Token "
                                    + termAtt.toString()
                                    + " exceeds length of provided text sized "
                                    + text.length());
                }
                if ((tokenGroup.getNumTokens() > 0) && (tokenGroup.isDistinct())) {
                    // the current token is distinct from previous tokens -
                    // markup the cached token group info
                    startOffset = tokenGroup.getStartOffset();
                    endOffset = tokenGroup.getEndOffset();

                    if (tokenGroup.getTotalScore() > 0.0F) {
                        matchLocations.add(new StringMatchLocation(startOffset, endOffset - startOffset));
                        if (matchLocations.size() >= maxMatches) {
                            return matchLocations;
                        }
                    }

//                    tokenText = text.substring(startOffset, endOffset);
//                    String markedUpText = formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);
                    // store any whitespace etc from between this and last group
//                    if (startOffset > lastEndOffset)
//                        newText.append(encoder.encodeText(text.substring(lastEndOffset, startOffset)));
//                    newText.append(markedUpText);
//                    lastEndOffset = Math.max(endOffset, lastEndOffset);
                    tokenGroup.clear();

                    // check if current token marks the start of a new fragment
                    if (textFragmenter.isNewFragment()) {
                        currentFrag.setScore(fragmentScorer.getFragmentScore());
                        // record stats for a new fragment
                        currentFrag.textEndPos = newText.length();
                        currentFrag = new TextFragImpl(newText, newText.length(), fragCount);
                        fragmentScorer.startFragment(currentFrag);
                        fragCount++;
//                        docFrags.add(currentFrag);
                    }
                }

                tokenGroup.addToken(fragmentScorer.getTokenScore());

                //        if(lastEndOffset>maxDocBytesToAnalyze)
                //        {
                //          break;
                //        }
            }
//            currentFrag.setScore(fragmentScorer.getFragmentScore());

//            if (tokenGroup.getNumTokens() > 0) {
//                // flush the accumulated text (same code as in above loop)
//                startOffset = tokenGroup.getStartOffset();
//                endOffset = tokenGroup.getEndOffset();
//                tokenText = text.substring(startOffset, endOffset);
//                String markedUpText = formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);
//                // store any whitespace etc from between this and last group
//                if (startOffset > lastEndOffset)
//                    newText.append(encoder.encodeText(text.substring(lastEndOffset, startOffset)));
//                newText.append(markedUpText);
//                lastEndOffset = Math.max(lastEndOffset, endOffset);
//            }

//            // Test what remains of the original text beyond the point where we stopped analyzing
//            if (
//                //          if there is text beyond the last token considered..
//                    (lastEndOffset < text.length())
//                            &&
//                            //          and that text is not too large...
//                            (text.length() <= maxDocCharsToAnalyze)) {
//                // append it to the last fragment
//                newText.append(encoder.encodeText(text.substring(lastEndOffset)));
//            }
//
//            currentFrag.textEndPos = newText.length();

//            // sort the most relevant sections of the text
//            for (Iterator<TextFragImpl> i = docFrags.iterator(); i.hasNext(); ) {
//                currentFrag = i.next();
//
//                // If you are running with a version of Lucene before 11th Sept 03
//                // you do not have PriorityQueue.insert() - so uncomment the code below
//        /*
//                  if (currentFrag.getScore() >= minScore)
//                  {
//                    fragQueue.put(currentFrag);
//                    if (fragQueue.size() > maxNumFragments)
//                    { // if hit queue overfull
//                      fragQueue.pop(); // remove lowest in hit queue
//                      minScore = ((TextFragment) fragQueue.top()).getScore(); // reset minScore
//                    }
//
//
//                  }
//        */
//                // The above code caused a problem as a result of Christoph Goller's 11th Sept 03
//                // fix to PriorityQueue. The correct method to use here is the new "insert" method
//                // USE ABOVE CODE IF THIS DOES NOT COMPILE!
////                fragQueue.insertWithOverflow(currentFrag);
//                fragQueue.add(currentFrag);
//            }

//            // return the most relevant fragments
//            TextFragment[] frag = new TextFragment[fragQueue.size()];
//            for (int i = frag.length - 1; i >= 0; i--) {
//                frag[i] = fragQueue.removeLast();
//            }
//
//            // merge any contiguous fragments to improve readability
//            if (mergeContiguousFragments) {
//                mergeContiguousFragments(frag);
//                ArrayList<TextFragment> fragTexts = new ArrayList<>();
//                for (int i = 0; i < frag.length; i++) {
//                    if ((frag[i] != null) && (frag[i].getScore() > 0)) {
//                        fragTexts.add(frag[i]);
//                    }
//                }
//                frag = fragTexts.toArray(new TextFragment[0]);
//            }


        } finally {
            if (tokenStream != null) {
                try {
                    tokenStream.end();
                    tokenStream.close();
                } catch (
                        @SuppressWarnings("unused")
                        Exception e) {
                }
            }
        }

        return matchLocations;
    }
}

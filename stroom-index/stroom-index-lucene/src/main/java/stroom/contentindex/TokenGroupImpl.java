package stroom.contentindex;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.search.highlight.TokenGroup;

public class TokenGroupImpl extends TokenGroup {

    private static final int MAX_NUM_TOKENS_PER_GROUP = 50;
    private float[] scores = new float[50];
    private int numTokens = 0;
    private int startOffset = 0;
    private int endOffset = 0;
    private float tot;
    private int matchStartOffset;
    private int matchEndOffset;
    private OffsetAttribute offsetAtt;

    public TokenGroupImpl(final TokenStream tokenStream) {
        super(tokenStream);
        this.offsetAtt = (OffsetAttribute) tokenStream.addAttribute(OffsetAttribute.class);
        tokenStream.addAttribute(CharTermAttribute.class);
    }

    void addToken(final float score) {
        if (this.numTokens < 50) {
            final int termStartOffset = this.offsetAtt.startOffset();
            final int termEndOffset = this.offsetAtt.endOffset();
            if (this.numTokens == 0) {
                this.startOffset = this.matchStartOffset = termStartOffset;
                this.endOffset = this.matchEndOffset = termEndOffset;
                this.tot += score;
            } else {
                this.startOffset = Math.min(this.startOffset, termStartOffset);
                this.endOffset = Math.max(this.endOffset, termEndOffset);
                if (score > 0.0F) {
                    if (this.tot == 0.0F) {
                        this.matchStartOffset = termStartOffset;
                        this.matchEndOffset = termEndOffset;
                    } else {
                        this.matchStartOffset = Math.min(this.matchStartOffset, termStartOffset);
                        this.matchEndOffset = Math.max(this.matchEndOffset, termEndOffset);
                    }

                    this.tot += score;
                }
            }

            this.scores[this.numTokens] = score;
            ++this.numTokens;
        }

    }

    boolean isDistinct() {
        return this.offsetAtt.startOffset() >= this.endOffset;
    }

    void clear() {
        this.numTokens = 0;
        this.tot = 0.0F;
    }

    public float getScore(final int index) {
        return this.scores[index];
    }

    public int getStartOffset() {
        return this.matchStartOffset;
    }

    public int getEndOffset() {
        return this.matchEndOffset;
    }

    public int getNumTokens() {
        return this.numTokens;
    }

    public float getTotalScore() {
        return this.tot;
    }
}

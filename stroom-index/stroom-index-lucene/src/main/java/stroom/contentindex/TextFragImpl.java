package stroom.contentindex;

import org.apache.lucene.search.highlight.TextFragment;

public class TextFragImpl extends TextFragment {

    CharSequence markedUpText;
    int fragNum;
    int textStartPos;
    int textEndPos;
    float score;

    public TextFragImpl(final CharSequence markedUpText, final int textStartPos, final int fragNum) {
        super(markedUpText, textStartPos, fragNum);
        this.markedUpText = markedUpText;
        this.textStartPos = textStartPos;
        this.fragNum = fragNum;
    }

    void setScore(final float score) {
        this.score = score;
    }

    public float getScore() {
        return this.score;
    }

    public void merge(final TextFragImpl frag2) {
        this.textEndPos = frag2.textEndPos;
        this.score = Math.max(this.score, frag2.score);
    }

    public boolean follows(final TextFragImpl fragment) {
        return this.textStartPos == fragment.textEndPos;
    }

    public int getFragNum() {
        return this.fragNum;
    }

    public String toString() {
        return this.markedUpText.subSequence(this.textStartPos, this.textEndPos).toString();
    }
}

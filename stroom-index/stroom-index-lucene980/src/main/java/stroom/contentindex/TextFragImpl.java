package stroom.contentindex;

import org.apache.lucene980.search.highlight.TextFragment;

public class TextFragImpl extends TextFragment {

    CharSequence markedUpText;
    int fragNum;
    int textStartPos;
    int textEndPos;
    float score;

    public TextFragImpl(CharSequence markedUpText, int textStartPos, int fragNum) {
        super(markedUpText, textStartPos, fragNum);
        this.markedUpText = markedUpText;
        this.textStartPos = textStartPos;
        this.fragNum = fragNum;
    }

    void setScore(float score) {
        this.score = score;
    }

    public float getScore() {
        return this.score;
    }

    public void merge(TextFragImpl frag2) {
        this.textEndPos = frag2.textEndPos;
        this.score = Math.max(this.score, frag2.score);
    }

    public boolean follows(TextFragImpl fragment) {
        return this.textStartPos == fragment.textEndPos;
    }

    public int getFragNum() {
        return this.fragNum;
    }

    public String toString() {
        return this.markedUpText.subSequence(this.textStartPos, this.textEndPos).toString();
    }
}

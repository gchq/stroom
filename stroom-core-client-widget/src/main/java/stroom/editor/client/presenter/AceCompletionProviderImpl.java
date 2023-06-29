package stroom.editor.client.presenter;

import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import java.util.ArrayList;
import java.util.List;

public class AceCompletionProviderImpl implements AceCompletionProvider {

    private final List<AceCompletion> completions = new ArrayList<>();

    @Override
    public void getProposals(final AceEditor editor,
                             final AceEditorCursorPosition pos,
                             final String prefix,
                             final AceCompletionCallback callback) {

        callback.invokeWithCompletions(completions.toArray(new AceCompletion[0]));
    }

    public void setCompletions(final List<AceCompletion> completions) {
        this.completions.clear();
        this.completions.addAll(completions);
    }

    public void addCompletions(final List<AceCompletion> completions) {
        this.completions.addAll(completions);
    }

    public void addCompletion(final AceCompletion completion) {
        this.completions.add(completion);
    }

    public void clear() {
        this.completions.clear();
    }

}

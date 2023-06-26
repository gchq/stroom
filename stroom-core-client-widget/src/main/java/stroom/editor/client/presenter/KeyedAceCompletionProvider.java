package stroom.editor.client.presenter;

import stroom.util.shared.GwtNullSafe;

import com.google.gwt.core.client.GWT;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeyedAceCompletionProvider implements AceCompletionProvider {

    private final Map<String, List<AceCompletion>> completionsMap = new HashMap<>();

    @Override
    public void getProposals(final AceEditor editor,
                             final AceEditorCursorPosition pos,
                             final String prefix,
                             final AceCompletionCallback callback) {

        final AceCompletion[] completionsArr = completionsMap.values()
                .stream()
                .flatMap(List::stream)
                .toArray(AceCompletion[]::new);

        GWT.log("getProposals, counts: " + completionsMap.entrySet()
                .stream()
                        .map(entry -> entry.getKey() + ":" + entry.getValue().size())
                        .collect(Collectors.joining(", ")));

        callback.invokeWithCompletions(completionsArr);
    }

    public void setCompletionsMap(final Map<String, List<AceCompletion>> completionsMap) {
        this.completionsMap.clear();
        this.completionsMap.putAll(completionsMap);
    }

    public void addCompletions(final String key, final List<AceCompletion> completions) {
        this.completionsMap.computeIfAbsent(key, k -> new ArrayList<>())
                .addAll(completions);
    }

    public void addCompletion(final String key, final AceCompletion completion) {
        this.completionsMap.computeIfAbsent(key, k -> new ArrayList<>())
                .add(completion);
    }

    public void clear(final String key) {
        GwtNullSafe.consume(this.completionsMap.get(key), List::clear);
    }

    public void clear() {
        this.completionsMap.clear();
    }
}

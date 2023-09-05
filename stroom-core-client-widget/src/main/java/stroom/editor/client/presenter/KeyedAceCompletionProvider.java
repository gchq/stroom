package stroom.editor.client.presenter;

import stroom.util.shared.GwtNullSafe;

import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class KeyedAceCompletionProvider implements AceCompletionProvider {

    private final Map<String, List<AceCompletion>> completionsMap = new HashMap<>();
    private final Map<String, List<LazyCompletion>> lazyCompletionsMap = new HashMap<>();

    @Override
    public void getProposals(final AceEditor editor,
                             final AceEditorCursorPosition pos,
                             final String prefix,
                             final AceCompletionCallback callback) {

        final List<AceCompletion> completions = completionsMap.values()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toCollection(ArrayList::new));

//        GWT.log("completionsMap, counts: " + completionsMap.entrySet()
//                .stream()
//                        .map(entry -> entry.getKey() + ":" + entry.getValue().size())
//                        .collect(Collectors.joining(", ")));

        @SuppressWarnings("SimplifyStreamApiCallChains") // GWT
        final List<LazyCompletion> lazyCompletions = lazyCompletionsMap.values()
                .stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

//        GWT.log("lazyCompletionsMap, counts: " + lazyCompletionsMap.entrySet()
//                .stream()
//                        .map(entry -> entry.getKey() + ":" + entry.getValue().size())
//                        .collect(Collectors.joining(", ")));

        final int lazyCompletionCount = lazyCompletions.size();
        final AtomicInteger lazyCompletionsSuppliedCount = new AtomicInteger(0);

        if (lazyCompletionCount > 0) {
            for (final LazyCompletion lazyCompletion : lazyCompletions) {
                lazyCompletion.supplyCompletion(aceCompletion -> {
                    lazyCompletionsSuppliedCount.incrementAndGet();
                    if (aceCompletion != null) {
                        completions.add(aceCompletion);
                    }
                    if (lazyCompletionsSuppliedCount.get() >= lazyCompletionCount) {
                        callback.invokeWithCompletions(completions.toArray(new AceCompletion[0]));
                    }
                });
            }
        } else {
            callback.invokeWithCompletions(completions.toArray(new AceCompletion[0]));
        }


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

    public void addLazyCompletion(final String key, final LazyCompletion lazyCompletion) {
        this.lazyCompletionsMap.computeIfAbsent(key, k -> new ArrayList<>())
                .add(lazyCompletion);
    }

    public void clear(final String key) {
        GwtNullSafe.consume(this.completionsMap.get(key), List::clear);
        GwtNullSafe.consume(this.lazyCompletionsMap.get(key), List::clear);
    }

    public void clear() {
        this.completionsMap.clear();
        this.lazyCompletionsMap.clear();
    }
}

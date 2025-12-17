/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.editor.client.presenter;

import com.google.gwt.core.client.GWT;
import com.google.inject.Singleton;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * For reasons I haven't yet figured out, when you add a completer to the editor.completers array
 * the completer is visible to ALL editor instances. While the AceCompletionProvider
 * allows us to only provide completions if it matches our editorId, if we keep adding
 * a completion provider for each new editor instance then we will end up with loads of them.
 * <p>
 * Thus the idea of this class is to provide a single completion provider instance that is added once
 * but then delegates getting the actual completions to currently registered providers.
 * <p>
 * Each user of an EditorPresenter can register one or more AceCompletionProviders for its
 * editor instance.  When completions are required this class will consult all currently registered
 * providers for the editor instance requesting completions.
 * <p>
 * This class should be a singleton to ensure it only supplies one completion provider.
 */
// See this jsfiddle for an example of how the completers array changes on multiple editors
// https://jsfiddle.net/g3fqaonh/3/
@Singleton
public class DelegatingAceCompleter {

    private final Map<MapKey, List<AceCompletionProvider>> editorIdToCompletionProviderMap = new HashMap<>();

    public DelegatingAceCompleter() {

        // Set up ACE with the delegating provider that applies to all Ace editor instances
        AceEditor.addCompletionProvider(this::getProposals);
    }

    /**
     * Register completion providers specific to an editor and a mode
     */
    public void registerCompletionProviders(final String editorId,
                                            final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
//        GWT.log("Registering " + completionProviders.length
//                + " with id " + editorId
//                + " and mode " + aceEditorMode);

        final String modeName = aceEditorMode != null
                ? aceEditorMode.getName()
                : null;
        final MapKey mapKey = MapKey.from(editorId, modeName);
        editorIdToCompletionProviderMap.computeIfAbsent(
                        mapKey, k -> new ArrayList<>())
                .addAll(Arrays.asList(completionProviders));

        logCompletionProvidersCount();
    }

    private void logCompletionProvidersCount() {
//        GWT.log("Delegated completers count: " + editorIdToCompletionProviderMap.values().stream()
//                .mapToLong(List::size)
//                .sum());
    }

    /**
     * Register mode agnostic completion providers specific to an editor
     */
    public void registerCompletionProviders(final String editorId,
                                            final AceCompletionProvider... completionProviders) {
        registerCompletionProviders(editorId, null, completionProviders);
    }

    /**
     * Register editor instance agnostic completion providers specific to a mode
     */
    public void registerCompletionProviders(final AceEditorMode aceEditorMode,
                                            final AceCompletionProvider... completionProviders) {
        registerCompletionProviders(null, aceEditorMode, completionProviders);
    }

    /**
     * Remove all providers specific to this editor regardless of editor mode
     */
    public void deRegisterCompletionProviders(final String editorId) {
//        GWT.log("DeRegistering id " + editorId);
        if (editorId != null) {
            editorIdToCompletionProviderMap.keySet()
                    .stream()
                    .filter(mapKey -> mapKey.getEditorId().equals(editorId))
                    .forEach(editorIdToCompletionProviderMap::remove);
        }
        logCompletionProvidersCount();
    }

    /**
     * Remove all providers for this mode. These providers may be used by many
     * editor instances.
     */
    public void deRegisterCompletionProviders(final AceEditorMode mode) {
//        GWT.log("DeRegistering mode " + mode);
        if (mode != null) {
            editorIdToCompletionProviderMap.remove(MapKey.fromModeName(mode.getName()));
        }
        logCompletionProvidersCount();
    }

    private void getProposals(final AceEditor editor,
                              final AceEditorCursorPosition pos,
                              final String prefix,
                              final AceCompletionCallback callback) {

        final String editorId = editor.getId();
        final String modeShortName = editor.getModeShortName();

        GWT.log("getProposals() - editorId: " + editorId + ", modeShortName: " + modeShortName
                + ", prefix: '" + prefix + "'");

        final List<AceCompletionProvider> completionProviders = getApplicableCompletionProviders(
                editorId, modeShortName);

        final List<AceCompletion> allCompletions = new ArrayList<>();
        final AtomicInteger addedCount = new AtomicInteger(0);

        // Aggregate the proposals from multiple completion providers, e.g. one for the Ace mode,
        // one for the specific editor, etc.
        completionProviders.forEach(completionProvider -> {
            if (completionProvider != null) {
                completionProvider.getProposals(editor, pos, prefix, proposals -> {
                    allCompletions.addAll(Arrays.asList(proposals));
                    if (addedCount.incrementAndGet() >= completionProviders.size()) {
                        // Now we have all the completions we can call back to Ace
                        // with the completions. This assumes we want to send all at once.
                        // Maybe Ace allows us to call the callback multiple times with an updated
                        // snapshot of completions each time.
                        callback.invokeWithCompletions(
                                allCompletions.toArray(new AceCompletion[allCompletions.size()]));
                    }
                });
            }
        });
    }

    private List<AceCompletionProvider> getApplicableCompletionProviders(final String editorId,
                                                                         final String modeShortName) {

        final List<AceCompletionProvider> allCompletionProviders = new ArrayList<>();
        // Add any mode agnostic providers for this editor
        addProviders(allCompletionProviders, MapKey.fromEditorId(editorId));
        // Add any mode specific providers for this editor
        addProviders(allCompletionProviders, MapKey.from(editorId, modeShortName));
        // Add any editor agnostic providers for this mode
        addProviders(allCompletionProviders, MapKey.fromModeName(modeShortName));
        return allCompletionProviders;
    }

    private void addProviders(final List<AceCompletionProvider> allCompletionProviders,
                              final MapKey mapKey) {

        final List<AceCompletionProvider> completionProviders = editorIdToCompletionProviderMap.get(mapKey);
        if (completionProviders != null) {
            GWT.log("Adding " + completionProviders.size() + " providers for " + mapKey);
            allCompletionProviders.addAll(completionProviders);
        }
    }


    // --------------------------------------------------------------------------------


    private static class MapKey {

        private final String editorId;
        private final String modeName;

        private MapKey(final String editorId, final String modeName) {
            this.editorId = editorId;
            this.modeName = modeName;
        }

        public static MapKey from(final String editorId, final String modeName) {
            return new MapKey(editorId, modeName);
        }

        public static MapKey fromEditorId(final String editorId) {
            return new MapKey(editorId, null);
        }

        public static MapKey fromModeName(final String modeName) {
            return new MapKey(null, modeName);
        }

        public String getEditorId() {
            return editorId;
        }

        public String getModeName() {
            return modeName;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MapKey mapKey = (MapKey) o;
            return Objects.equals(editorId, mapKey.editorId) &&
                   Objects.equals(modeName, mapKey.modeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(editorId, modeName);
        }
    }
}

/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.pipeline.textconverter.TextConverterStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// What this environment reads XML fragments with: its own XML_FRAGMENT text converters, and the event-logging
/// version an `<Events>` wrapper is written in (the Stroom MCP server's `tools/translation.py`
/// `fragment_wrappers`, and the version `with_fragment_setup` reads through `chosen_or_latest`).
class FragmentWrappers {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FragmentWrappers.class);

    /// The most text converters looked at, as the Stroom MCP server's search asked for.
    static final int LIMIT = 100;

    private final Provider<TextConverterStore> textConverterStoreProvider;
    private final DocumentAccess documentAccess;
    private final Provider<WriteGuard> guardProvider;
    private final HeldSchemas heldSchemas;

    /// @param textConverterStoreProvider Lists the text converters.
    /// @param documentAccess             Reads each the user may view, and its path.
    /// @param guardProvider              Names the workspace folder, whose converters are the builds' own.
    /// @param heldSchemas                The event-logging schemas this Stroom holds.
    @Inject
    FragmentWrappers(final Provider<TextConverterStore> textConverterStoreProvider,
                     final DocumentAccess documentAccess,
                     final Provider<WriteGuard> guardProvider,
                     final HeldSchemas heldSchemas) {
        this.textConverterStoreProvider = textConverterStoreProvider;
        this.documentAccess = documentAccess;
        this.guardProvider = guardProvider;
        this.heldSchemas = heldSchemas;
    }

    /// The environment's XML_FRAGMENT converters outside the workspace. Seen: the server proposed its records:2
    /// wrapper where every wrapper in the environment was an event-logging:3 `<Events>` one.
    ///
    /// @return Each one's `name`, `uuid`, `path`, `code`, and the `root` and `namespace` the fragments are read in;
    /// none when they can't be listed (a convenience on top of the standard wrapper: a profile never fails over it).
    List<Map<String, Object>> find() {
        try {
            final String workspace = guardProvider.get().workspaceFolder();
            final List<DocRef> refs = NullSafe.list(textConverterStoreProvider.get().list());
            final List<Map<String, Object>> found = new ArrayList<>();
            for (final DocRef ref : refs.subList(0, Math.min(LIMIT, refs.size()))) {
                wrapper(ref, workspace).ifPresent(found::add);
            }
            return found;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to list the fragment wrappers: {}", e.getMessage()), e);
            return List.of();
        }
    }

    /// @param chosen The event-logging version the user chose for the build, or null if none was chosen.
    /// @return It, else the newest this Stroom holds; null when it holds none (no `<Events>` wrapper to offer).
    String version(final String chosen) {
        try {
            return heldSchemas.chosenOrLatest(chosen);
        } catch (final ToolRefusal e) {
            return null;
        }
    }

    // One converter, if it is a fragment wrapper outside the workspace.
    private Optional<Map<String, Object>> wrapper(final DocRef ref, final String workspace) {
        // Deleted since it was listed, or not this user's to read: not one of them.
        final Optional<TextConverterDoc> doc = documentAccess.read(ref, TextConverterDoc.class);
        if (doc.isEmpty()
            || doc.get().getConverterType() != TextConverterType.XML_FRAGMENT
            || !NullSafe.string(doc.get().getData()).contains("&fragment;")) {
            return Optional.empty();
        }
        final String path = documentAccess.path(ref);
        if (inWorkspace(path, workspace)) {
            return Optional.empty();
        }
        final String code = doc.get().getData();
        final String[] root = SampleProfiler.wrapperRoot(code);
        final Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("name", ref.getName());
        wrapper.put("uuid", ref.getUuid());
        wrapper.put("path", path);
        wrapper.put("code", code);
        wrapper.put("root", root[0]);
        wrapper.put("namespace", root[1]);
        return Optional.of(wrapper);
    }

    /// @param path      A document's folder path, e.g. `/MCP Workspace/acme-v1`.
    /// @param workspace The workspace folder's name.
    /// @return True if the path is in the workspace (a build's own converter, not the environment's convention).
    static boolean inWorkspace(final String path, final String workspace) {
        return Arrays.asList(NullSafe.string(path).split("/")).contains(workspace);
    }
}

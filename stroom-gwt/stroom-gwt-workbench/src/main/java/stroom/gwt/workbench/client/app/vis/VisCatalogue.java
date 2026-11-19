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

package stroom.gwt.workbench.client.app.vis;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The visualisations and scripts of a content pack, as the workbench server serves them from
/// `/__workbench/content/visualisations` (its `StroomContent`), and Stroom's replies about them:
/// a visualisation document and the scripts it needs.
public final class VisCatalogue {

    private final Map<String, Map<?, ?>> visualisationsByName = new LinkedHashMap<>();
    private final Map<String, String> settingsByName = new LinkedHashMap<>();
    private final Map<String, Boolean> indexAssetByName = new LinkedHashMap<>();
    private final Map<String, Map<?, ?>> scriptsByUuid = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    private VisCatalogue() {
    }

    /// Reads the catalogue. A document whose `.meta` isn't a JSON object, or whose settings aren't
    /// JSON, is skipped and reported in [#getProblems()].
    ///
    /// @param json The JSON the workbench server serves: `{"visualisations": [{"path": "...",
    ///             "meta": "<JSON>", "settings": "<JSON>", "indexAsset": true}], "scripts":
    ///             [{"path": "...", "meta": "<JSON>"}]}`. `indexAsset` may be left out (false).
    /// @return The catalogue.
    /// @throws IllegalArgumentException If the JSON isn't of that form.
    public static VisCatalogue parse(final String json) {
        final VisCatalogue catalogue = new VisCatalogue();
        final Object parsed = JsonValues.parse(json);
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException("Not a catalogue of visualisations: " + json);
        }
        final Map<?, ?> map = (Map<?, ?>) parsed;
        for (final Object visualisation : list(map.get("visualisations"))) {
            final Map<?, ?> file = (Map<?, ?>) visualisation;
            final Map<?, ?> meta = catalogue.meta(file);
            final Object settings = file.get("settings");
            if (meta != null && (settings == null || catalogue.isJson(file, String.valueOf(settings)))) {
                final String name = String.valueOf(meta.get("name"));
                // The first of two with a name wins, as the files are in the order of their paths
                if (!catalogue.visualisationsByName.containsKey(name)) {
                    catalogue.visualisationsByName.put(name, meta);
                    catalogue.settingsByName.put(name, settings == null
                            ? null
                            : String.valueOf(settings));
                    catalogue.indexAssetByName.put(name, Boolean.TRUE.equals(file.get("indexAsset")));
                }
            }
        }
        for (final Object script : list(map.get("scripts"))) {
            final Map<?, ?> meta = catalogue.meta((Map<?, ?>) script);
            if (meta != null) {
                catalogue.scriptsByUuid.put(String.valueOf(meta.get("uuid")), meta);
            }
        }
        return catalogue;
    }

    /// @return Why documents were skipped, e.g. `Visualisations/Version3/X.Script.uuid.meta isn't
    /// a JSON object`.
    public List<String> getProblems() {
        return problems;
    }

    /// @return The names of the visualisations, in the order they were served.
    public Collection<String> getNames() {
        return visualisationsByName.keySet();
    }

    /// @param name A visualisation's name, e.g. `BarChart`.
    /// @return True if there is a visualisation with the name.
    public boolean has(final String name) {
        return visualisationsByName.containsKey(name);
    }

    /// @param name A visualisation's name.
    /// @return Its UUID.
    /// @throws IllegalArgumentException If there is no visualisation with the name.
    public String uuid(final String name) {
        return String.valueOf(meta(name).get("uuid"));
    }

    /// @param name A visualisation's name.
    /// @return True if it is asset-based: Stroom loads its `index.html` asset into its frame
    /// rather than injecting its scripts into `vis.html`.
    /// @throws IllegalArgumentException If there is no visualisation with the name.
    public boolean hasIndexAsset(final String name) {
        meta(name);
        return indexAssetByName.get(name);
    }

    /// @param name A visualisation's name.
    /// @return Its settings: its controls and the structure of its data.
    /// @throws IllegalArgumentException If there is no visualisation with the name, or it has no
    ///                                  settings.
    public String settings(final String name) {
        meta(name);
        final String settings = settingsByName.get(name);
        if (settings == null) {
            throw new IllegalArgumentException("The visualisation " + name + " has no settings");
        }
        return settings;
    }

    /// The visualisation's document (`VisualisationDoc`), as `GET /visualisation/v1/{uuid}`
    /// returns it.
    ///
    /// @param name A visualisation's name.
    /// @return The document's JSON.
    /// @throws IllegalArgumentException If there is no visualisation with the name.
    public String visualisationDocJson(final String name) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : meta(name).entrySet()) {
            doc.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        doc.put("settings", settingsByName.get(name));
        return JsonValues.toJson(doc);
    }

    /// The scripts that a script needs, as Stroom's `ScriptStoreImpl.fetchLinkedScripts` returns
    /// them (for `POST /script/v1/fetchLinkedScripts`): each script's dependencies before it, each
    /// once, leaving out those the frame has already loaded. Their data, which the frame loads
    /// from the script servlet, is left out.
    ///
    /// @param scriptUuid    The UUID of the script, e.g. a visualisation's.
    /// @param loadedScripts The UUIDs of the scripts the frame has loaded.
    /// @return The JSON array of the scripts (`ScriptDoc`s).
    public String linkedScriptsJson(final String scriptUuid, final Collection<String> loadedScripts) {
        final List<Object> scripts = new ArrayList<>();
        addScripts(scriptUuid, new HashSet<>(loadedScripts), new HashSet<>(), scripts);
        return JsonValues.toJson(scripts);
    }

    /// The reply to `POST /script/v1/fetchLinkedScripts`.
    ///
    /// @param requestJson The request's body (`FetchLinkedScriptRequest`): the script, and the
    ///                    scripts the frame has loaded.
    /// @return The JSON array of the scripts, see [#linkedScriptsJson].
    public String linkedScriptsReply(final String requestJson) {
        final Map<?, ?> request = (Map<?, ?>) JsonValues.parse(requestJson);
        final Map<?, ?> script = (Map<?, ?>) request.get("script");
        final List<String> loaded = new ArrayList<>();
        for (final Object docRef : list(request.get("loadedScripts"))) {
            loaded.add(String.valueOf(((Map<?, ?>) docRef).get("uuid")));
        }
        return linkedScriptsJson(String.valueOf(script.get("uuid")), loaded);
    }

    private void addScripts(final String uuid,
                            final Set<String> uiLoaded,
                            final Set<String> visited,
                            final List<Object> scripts) {
        // The visited set stops circular dependencies looping
        if (!visited.add(uuid)) {
            return;
        }
        final Map<?, ?> meta = scriptsByUuid.get(uuid);
        if (meta == null) {
            return;
        }
        for (final Object dependency : list(meta.get("dependencies"))) {
            addScripts(String.valueOf(((Map<?, ?>) dependency).get("uuid")), uiLoaded, visited, scripts);
        }
        if (uiLoaded.add(uuid)) {
            final Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("type", "Script");
            doc.put("uuid", uuid);
            doc.put("name", meta.get("name"));
            doc.put("version", meta.get("version"));
            scripts.add(doc);
        }
    }

    // A file's .meta, or null (noting the problem) if it isn't a JSON object
    private Map<?, ?> meta(final Map<?, ?> file) {
        try {
            final Object meta = JsonValues.parse(String.valueOf(file.get("meta")));
            if (meta instanceof Map) {
                return (Map<?, ?>) meta;
            }
        } catch (final IllegalArgumentException e) {
            // Noted below
        }
        problems.add(file.get("path") + " isn't a JSON object");
        return null;
    }

    // Whether a visualisation's settings are JSON, noting the problem if they aren't
    private boolean isJson(final Map<?, ?> file, final String settings) {
        try {
            JsonValues.parse(settings);
            return true;
        } catch (final IllegalArgumentException e) {
            problems.add("The settings of " + file.get("path") + " aren't JSON: " + e.getMessage());
            return false;
        }
    }

    private Map<?, ?> meta(final String name) {
        final Map<?, ?> meta = visualisationsByName.get(name);
        if (meta == null) {
            throw new IllegalArgumentException("There is no visualisation " + name + ", only "
                                               + visualisationsByName.keySet());
        }
        return meta;
    }

    private static List<?> list(final Object value) {
        return value instanceof List
                ? (List<?>) value
                : List.of();
    }
}

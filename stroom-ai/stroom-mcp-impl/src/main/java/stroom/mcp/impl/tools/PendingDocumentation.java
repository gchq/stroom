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

import stroom.ai.impl.tools.ToolSession;

import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// A pipeline's or an index's documentation not yet written, for the session: write_documentation asked for field
/// descriptions
/// (`needs_descriptions`) and wrote nothing. Seen in testing: descriptions given over several calls were lost, as
/// they were kept only with a written doc, so each round asked for every field again; the markdown was sent whole in
/// each of nine calls; and the agent, its reply moved to a file, said "Documentation is done" and went on to indexing
/// with nothing written. So what was given is kept here until the doc is written, and the next stage's tools are
/// told of documentation still pending.
@Singleton
class PendingDocumentation {

    /// The most pending docs remembered: the least recently used are forgotten first.
    static final int MAX_REMEMBERED = 1_000;

    // By session and pipeline.
    private final Map<String, Pending> pending = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Pending> eldest) {
                    return size() > MAX_REMEMBERED;
                }
            });

    /// Remembers what a call gave for a pipeline's doc that wasn't written.
    ///
    /// @param session      The session.
    /// @param pipelineUuid The pipeline documented.
    /// @param pipelineName Its name.
    /// @param markdown     The markdown given.
    /// @param descriptions Every description given so far.
    void remember(final ToolSession session,
                  final String pipelineUuid,
                  final String pipelineName,
                  final String markdown,
                  final Map<String, String> descriptions) {
        pending.put(key(session, pipelineUuid), new Pending(pipelineUuid, "pipeline '" + pipelineName + "'",
                markdown, Map.copyOf(descriptions)));
    }

    /// Remembers what a call gave for an existing index's doc that wasn't written (seen in testing: its
    /// descriptions, given in batches, were each lost with the call that gave them).
    ///
    /// @param session      The session.
    /// @param indexUuid    The index documented.
    /// @param indexName    Its name.
    /// @param markdown     The markdown given.
    /// @param descriptions Every description given so far.
    void rememberIndex(final ToolSession session,
                       final String indexUuid,
                       final String indexName,
                       final String markdown,
                       final Map<String, String> descriptions) {
        pending.put(key(session, indexUuid), new Pending(indexUuid, "index '" + indexName + "'", markdown,
                Map.copyOf(descriptions)));
    }

    /// @param session The session.
    /// @param uuid    A pipeline or an index.
    /// @return What earlier calls gave for its doc, not yet written.
    Optional<Pending> of(final ToolSession session, final String uuid) {
        return Optional.ofNullable(pending.get(key(session, uuid)));
    }

    /// Forgets a doc once written.
    ///
    /// @param session The session.
    /// @param uuid    The pipeline or index documented.
    void written(final ToolSession session, final String uuid) {
        pending.remove(key(session, uuid));
    }

    /// @param session The session.
    /// @return The session's docs not yet written.
    List<Pending> of(final ToolSession session) {
        final String prefix = session + "|";
        synchronized (pending) {
            return pending.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .toList();
        }
    }

    /// @param session The session.
    /// @return What the next stage's tools say while a doc is pending, or empty.
    Optional<String> warning(final ToolSession session) {
        final List<Pending> open = of(session);
        if (open.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("The documentation of " + String.join(", ", open.stream()
                .map(Pending::label)
                .toList()) + " is not written yet: write_documentation asked for field descriptions and wrote "
                           + "nothing. Finish it first (write_documentation with field_descriptions; the markdown "
                           + "and the descriptions already given are kept), and don't tell the user it is done "
                           + "until it says written.");
    }

    private static String key(final ToolSession session, final String pipelineUuid) {
        return session + "|" + pipelineUuid;
    }


    // --------------------------------------------------------------------------------


    /// A doc not yet written.
    ///
    /// @param uuid         The pipeline or index.
    /// @param label        What it is, as the warning names it: `pipeline 'name'` or `index 'name'`.
    /// @param markdown     The markdown last given.
    /// @param descriptions The descriptions given so far.
    record Pending(String uuid, String label, String markdown, Map<String, String> descriptions) {

    }
}

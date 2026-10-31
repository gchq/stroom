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

package stroom.mcp.impl;

import stroom.ai.impl.tools.AgentKnowledge;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// The guides and field convention profiles bundled with Stroom for agents (`guides/` and `conventions/`), read from
/// the classpath under [#ROOT].
///
/// The files are listed here, not found by listing the folder, as a folder can't be listed reliably inside a jar.
@Singleton
public class BundledKnowledge implements AgentKnowledge {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BundledKnowledge.class);

    /// Where the files are on the classpath.
    static final String ROOT = "/stroom/mcp/impl/knowledge/";
    /// The guides, by name: each is `guides/<name>.md`.
    static final List<String> GUIDES = List.of(
            "agent-instructions",
            "cef",
            "data-splitter",
            "documentation",
            "event-logging",
            "indexing",
            "json-input",
            "reference-data",
            "visualisation",
            "xslt");
    /// The field convention profiles, by name: each is `conventions/<name>.yaml`.
    static final List<String> CONVENTIONS = List.of("ecs", "stroom-flat");
    /// The ECS reference files the indexing tools read, in `conventions/`.
    public static final String ECS_FIELDS = "ecs_fields.json";
    /// The ECS component template file the indexing tools read, in `conventions/`.
    public static final String ECS_COMPONENT = "ecs_component.json";

    /// The instructions every agent is given (the rules for every run).
    static final String SERVER_INSTRUCTIONS = "server-instructions.md";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /// @return What every client is told at connection, so the rules hold whichever client runs the model: the
    /// order of work, the rules for every run, how to ask the user, and so on.
    static String serverInstructions() {
        return readResource(SERVER_INSTRUCTIONS).strip();
    }

    @Override
    public Optional<String> buildInstructions() {
        return Optional.of(serverInstructions());
    }

    @Override
    public List<String> guideNames() {
        return GUIDES;
    }

    @Override
    public Optional<String> guide(final String name) {
        return GUIDES.contains(name)
                ? Optional.of(read("guides/" + name + ".md"))
                : Optional.empty();
    }

    @Override
    public List<String> conventionNames() {
        return CONVENTIONS;
    }

    @Override
    public Optional<String> convention(final String name) {
        return CONVENTIONS.contains(name)
                ? Optional.of(read("conventions/" + name + ".yaml"))
                : Optional.empty();
    }

    /// @param fileName A file in the conventions folder, e.g. [#ECS_FIELDS].
    /// @return Its text, or empty if there is no such file.
    @Override
    public Optional<String> conventionsFile(final String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) {
            return Optional.empty();
        }
        try {
            return Optional.of(read("conventions/" + fileName));
        } catch (final UncheckedIOException e) {
            LOGGER.debug("No conventions file {}", fileName, e);
            return Optional.empty();
        }
    }

    // A bundled file's text, read once.
    private String read(final String path) {
        return cache.computeIfAbsent(path, BundledKnowledge::readResource);
    }

    private static String readResource(final String path) {
        try (final InputStream in = BundledKnowledge.class.getResourceAsStream(ROOT + path)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("No bundled file " + ROOT + path));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

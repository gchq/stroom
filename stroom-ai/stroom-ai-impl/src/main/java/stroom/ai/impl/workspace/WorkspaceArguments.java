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

package stroom.ai.impl.workspace;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import net.thisptr.jackson.jq.JsonQuery;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/// Puts the content of workspace files into an MCP tool call's arguments, so the model can pass a large argument
/// (e.g. a mapping it has saved to {@code /outputs/}) without writing it all out in the call.
///
/// An argument value, at any depth, may be a reference to a file:
///
/// - an object {@code {"$workspace": "/outputs/x.json"}}, optionally with {@code "jq": ".mapping"} to give part of
///   it;
/// - or, for a parameter typed as a string, the string {@code "$workspace:/outputs/x.json"}.
///
/// Each is replaced with the file's content (or the jq query's result): the JSON it holds if it is JSON, so an
/// object argument stays an object, and otherwise its text. Files are read through [ChatWorkspace], so a file that
/// has expired, or whose source document the user can no longer read, is refused.
public class WorkspaceArguments {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(WorkspaceArguments.class);

    /// The key of an object that names a workspace file.
    public static final String REFERENCE_KEY = "$workspace";
    /// The key, beside [#REFERENCE_KEY], of a jq query giving the part of the file to put in.
    public static final String JQ_KEY = "jq";
    /// What a string that names a workspace file starts with.
    public static final String REFERENCE_PREFIX = "$workspace:";

    private static final Set<String> REFERENCE_KEYS = Set.of(REFERENCE_KEY, JQ_KEY);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    // Reads a file of one JSON value or one per line, as the jq tool does.
    private static final ObjectMapper VALUES_MAPPER = new ObjectMapper();

    private final ChatWorkspace workspace;

    @Inject
    public WorkspaceArguments(final ChatWorkspace workspace) {
        this.workspace = workspace;
    }

    /// Replaces each workspace file reference in a tool call's arguments with the file's content.
    ///
    /// @param chatId        The chat whose workspace holds the files.
    /// @param argumentsJson The arguments, as the model wrote them.
    /// @return The arguments to send, with how many references were replaced, or why they could not be. Arguments
    /// that hold no reference (or are not a JSON object) are given back as they are.
    public Expansion expand(final int chatId, final String argumentsJson) {
        if (argumentsJson == null || !argumentsJson.contains(REFERENCE_KEY)) {
            return Expansion.unchanged(argumentsJson);
        }
        final JsonNode arguments;
        try {
            arguments = MAPPER.readTree(argumentsJson);
        } catch (final JsonProcessingException e) {
            // Left for the tool to refuse, as any arguments it can't read are.
            return Expansion.unchanged(argumentsJson);
        }
        if (!(arguments instanceof final ObjectNode argumentsObject)) {
            return Expansion.unchanged(argumentsJson);
        }
        try {
            final int references = workspace.withTimeLimit(() -> expandChildren(chatId, argumentsObject, ""));
            if (references == 0) {
                return Expansion.unchanged(argumentsJson);
            }
            final String expanded = MAPPER.writeValueAsString(argumentsObject);
            final long limit = workspace.limits().getMaxToolArgumentBytes();
            final long bytes = expanded.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > limit) {
                return Expansion.failed(argumentsJson, "With the workspace files put in, the arguments would be "
                                                       + bytes + " bytes, but may be at most " + limit + ". Pass "
                                                       + "a smaller part of the files (e.g. with \"jq\").");
            }
            LOGGER.debug("expand() - Put {} workspace file(s) into the arguments for chatId={}, {} bytes",
                    references, chatId, bytes);
            return new Expansion(expanded, references, null);
        } catch (final WorkspaceException e) {
            return Expansion.failed(argumentsJson, e.getMessage());
        } catch (final JsonProcessingException e) {
            return Expansion.failed(argumentsJson, "The arguments could not be written out: "
                                                   + e.getOriginalMessage());
        }
    }

    // Replaces the references among a value's children, but not within the content put in for them.
    // Returns how many were replaced.
    private int expandChildren(final int chatId, final JsonNode node, final String where) {
        int references = 0;
        if (node instanceof final ObjectNode object) {
            final List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (final String name : names) {
                final String childWhere = where.isEmpty()
                        ? name
                        : where + "." + name;
                final JsonNode child = object.get(name);
                final JsonNode content = content(chatId, child, childWhere);
                if (content != null) {
                    object.set(name, content);
                    references++;
                } else {
                    references += expandChildren(chatId, child, childWhere);
                }
            }
        } else if (node instanceof final ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                final String childWhere = where + "[" + i + "]";
                final JsonNode child = array.get(i);
                final JsonNode content = content(chatId, child, childWhere);
                if (content != null) {
                    array.set(i, content);
                    references++;
                } else {
                    references += expandChildren(chatId, child, childWhere);
                }
            }
        }
        return references;
    }

    // Returns what to put in place of a value, if it is a reference, or null if it is not.
    private JsonNode content(final int chatId, final JsonNode value, final String where) {
        if (value.isTextual() && value.textValue().startsWith(REFERENCE_PREFIX)) {
            return read(chatId, value.textValue().substring(REFERENCE_PREFIX.length()), null, where);
        }
        if (!(value instanceof final ObjectNode object) || !object.has(REFERENCE_KEY)) {
            return null;
        }
        final Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            final String name = names.next();
            if (!REFERENCE_KEYS.contains(name)) {
                throw new WorkspaceException(where + ": a " + REFERENCE_KEY + " reference may only have \""
                                             + JQ_KEY + "\" beside it, not \"" + name + "\".");
            }
        }
        final JsonNode path = object.get(REFERENCE_KEY);
        final JsonNode jq = object.get(JQ_KEY);
        if (!path.isTextual()) {
            throw new WorkspaceException(where + ": " + REFERENCE_KEY + " must be a workspace path, e.g. "
                                         + "\"/outputs/x.json\".");
        }
        if (jq != null && !jq.isTextual()) {
            throw new WorkspaceException(where + ": \"" + JQ_KEY + "\" must be a jq query, e.g. \".mapping\".");
        }
        return read(chatId, path.textValue(), jq == null
                ? null
                : jq.textValue(), where);
    }

    // Reads a file, or the jq query's result of it, as JSON if it is JSON and as text otherwise.
    private JsonNode read(final int chatId, final String rawPath, final String query, final String where) {
        try {
            final WorkspacePath path = WorkspacePath.parse(rawPath);
            // Refuses a file that is missing, expired or whose source document the user can no longer read.
            final long size = workspace.size(chatId, path);
            final long limit = workspace.limits().getMaxToolArgumentBytes();
            if (size > limit) {
                throw new WorkspaceException(path + " is " + size + " bytes, but a tool call's arguments may be at "
                                             + "most " + limit + ". Pass a part of it with \"" + JQ_KEY + "\".");
            }
            final Path file = workspace.localFile(chatId, path);
            return query == null
                    ? readWhole(path, file)
                    : readQuery(path, file, SafeJq.compile(query));
        } catch (final WorkspaceException e) {
            throw new WorkspaceException(where + ": " + e.getMessage());
        }
    }

    private static JsonNode readWhole(final WorkspacePath path, final Path file) {
        final String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (final CharacterCodingException e) {
            throw new WorkspaceException(path + " is not text.");
        } catch (final IOException e) {
            throw new WorkspaceException(path + " could not be read: " + e.getMessage());
        }
        try {
            final JsonNode json = MAPPER.readTree(text);
            // An empty file reads as a missing node, and is put in as (empty) text.
            return json != null && !json.isMissingNode()
                    ? json
                    : JsonNodeFactory.instance.textNode(text);
        } catch (final JsonProcessingException e) {
            return JsonNodeFactory.instance.textNode(text);
        }
    }

    // As the jq tool reads a file: one JSON value, or one per line. One result is put in as it is, and several as
    // an array of them.
    private static JsonNode readQuery(final WorkspacePath path, final Path file, final JsonQuery query) {
        final List<JsonNode> results = new ArrayList<>();
        try (final MappingIterator<JsonNode> inputs = VALUES_MAPPER.readerFor(JsonNode.class)
                .readValues(file.toFile())) {
            while (inputs.hasNextValue()) {
                ToolDeadline.check();
                SafeJq.run(query, inputs.nextValue(), results::add);
            }
        } catch (final JsonProcessingException e) {
            throw new WorkspaceException(path + " is not JSON that jq can read: " + e.getOriginalMessage());
        } catch (final IOException e) {
            throw new WorkspaceException(path + " could not be read: " + e.getMessage());
        }
        if (results.isEmpty()) {
            throw new WorkspaceException("The jq query gave no result for " + path + ".");
        }
        return results.size() == 1
                ? results.getFirst()
                : JsonNodeFactory.instance.arrayNode().addAll(results);
    }

    /// What a call's arguments are once workspace files have been put in.
    ///
    /// @param arguments  The arguments to send: with the files' content, or as the model wrote them if there were
    ///                   no references or they could not be put in.
    /// @param references How many references were replaced.
    /// @param error      Why the references could not be put in, or null. The call is then not to be sent.
    public record Expansion(String arguments, int references, String error) {

        /// @return The arguments as they are, with no references replaced.
        public static Expansion unchanged(final String arguments) {
            return new Expansion(arguments, 0, null);
        }

        static Expansion failed(final String arguments, final String error) {
            return new Expansion(arguments, 0, error);
        }

        /// @return True if any reference was replaced.
        public boolean isExpanded() {
            return references > 0;
        }
    }
}

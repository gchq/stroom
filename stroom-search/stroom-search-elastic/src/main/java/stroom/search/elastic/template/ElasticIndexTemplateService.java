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

package stroom.search.elastic.template;

import stroom.search.elastic.template.ElasticIndexTemplatesResponse.ComponentTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.IndexTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.OverlappingTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.Status;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Reads the index template and component templates that apply to an index name from Elasticsearch.
///
/// It finds them as Elasticsearch itself would for a new index of that name:
/// 1. Resolves the index name, which may be a pattern or an alias, to the newest concrete index or data
///    stream it stands for, falling back to the index name itself if nothing resolves.
/// 2. Of the index templates whose index patterns match that name, takes the one with the highest priority.
/// 3. Reads each component template in that template's `composed_of`.
///
/// Each call can fail on its own, most often for want of privileges, so each part reports its own
/// outcome and a failed part never stops the others being returned. Only a cluster that cannot be reached
/// at all fails the whole read.
public class ElasticIndexTemplateService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticIndexTemplateService.class);

    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;

    @Inject
    public ElasticIndexTemplateService() {
    }

    /// Reads the templates that apply to an index name.
    ///
    /// @param indexName    The Elastic Index doc's index name, which may be a pattern or an alias.
    /// @param elasticGet   Makes the GET requests to the doc's cluster.
    /// @return The templates, each part with its own outcome.
    /// @throws IOException If the cluster could not be reached at all.
    public ElasticIndexTemplatesResponse read(final String indexName, final ElasticGet elasticGet)
            throws IOException {
        final Resolved resolved = resolve(indexName, elasticGet);
        final String templateTarget = Objects.requireNonNullElse(resolved.name(), indexName);

        final IndexTemplate indexTemplate = readIndexTemplate(templateTarget, elasticGet);
        final List<ComponentTemplate> componentTemplates = indexTemplate.getStatus() == Status.OK
                ? readComponentTemplates(indexTemplate.getBody(), elasticGet)
                : List.of();

        return new ElasticIndexTemplatesResponse(
                indexName,
                resolved.name(),
                resolved.message(),
                indexTemplate,
                componentTemplates);
    }

    /// Whether a name matches an Elasticsearch index pattern, as Elasticsearch matches them: `*` stands for
    /// any run of characters, including none, and every other character stands for itself.
    ///
    /// @param pattern An index pattern, e.g. `ecs-*`.
    /// @param name    An index or data stream name.
    /// @return True if the name matches.
    static boolean matchesIndexPattern(final String pattern, final String name) {
        if (pattern == null || name == null) {
            return false;
        }
        final String regex = Arrays.stream(pattern.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return name.matches(regex);
    }

    private Resolved resolve(final String indexName, final ElasticGet elasticGet) throws IOException {
        final ElasticGetResponse response = elasticGet.get("/_resolve/index/" + encode(indexName));
        if (!response.isSuccess()) {
            // A name that matches nothing is not a failure, the template is still found by pattern
            return response.statusCode() == HTTP_NOT_FOUND
                    ? new Resolved(null, null)
                    : new Resolved(null, errorMessage(response));
        }

        final Map<String, List<String>> candidates;
        try {
            candidates = resolveCandidates(JsonUtil.getMapper().readTree(response.body()));
        } catch (final JacksonException e) {
            return new Resolved(null, unreadableMessage("_resolve/index", e));
        }
        if (candidates.isEmpty()) {
            return new Resolved(null, null);
        }

        final Map<String, Long> creationDates = readCreationDates(indexName, elasticGet);
        return new Resolved(newest(candidates, creationDates), null);
    }

    // The names a template could be matched against, each with the concrete indices behind it. A data
    // stream stands in for its backing indices, as templates match the data stream's name, not theirs.
    private Map<String, List<String>> resolveCandidates(final JsonNode root) {
        final Map<String, List<String>> candidates = new LinkedHashMap<>();

        for (final JsonNode dataStream : root.path("data_streams")) {
            final String name = stringOrNull(dataStream.path("name"));
            if (name != null) {
                final List<String> backingIndices = candidates.computeIfAbsent(name, k -> new ArrayList<>());
                dataStream.path("backing_indices").forEach(index -> addIfNamed(backingIndices, index));
            }
        }

        for (final JsonNode index : root.path("indices")) {
            final String name = stringOrNull(index.path("name"));
            if (name != null) {
                final String dataStream = stringOrNull(index.path("data_stream"));
                if (dataStream != null) {
                    addIfAbsent(candidates.computeIfAbsent(dataStream, k -> new ArrayList<>()), name);
                } else {
                    addIfAbsent(candidates.computeIfAbsent(name, k -> new ArrayList<>()), name);
                }
            }
        }

        // An alias lists what it points at, which need not match the expression itself
        for (final JsonNode alias : root.path("aliases")) {
            for (final JsonNode index : alias.path("indices")) {
                final String name = stringOrNull(index);
                if (name != null && !isBackingIndexOf(candidates, name)) {
                    addIfAbsent(candidates.computeIfAbsent(name, k -> new ArrayList<>()), name);
                }
            }
            for (final JsonNode dataStream : alias.path("data_streams")) {
                final String name = stringOrNull(dataStream);
                if (name != null) {
                    candidates.computeIfAbsent(name, k -> new ArrayList<>());
                }
            }
        }
        return candidates;
    }

    // Creation dates of the concrete indices the index name stands for. Empty if they cannot be read, in
    // which case the newest is picked by name.
    private Map<String, Long> readCreationDates(final String indexName, final ElasticGet elasticGet)
            throws IOException {
        final ElasticGetResponse response = elasticGet.get(
                "/" + encode(indexName) + "/_settings/index.creation_date");
        if (!response.isSuccess()) {
            LOGGER.debug("Unable to read index creation dates for '{}', HTTP {}",
                    indexName, response.statusCode());
            return Map.of();
        }

        final Map<String, Long> creationDates = new HashMap<>();
        try {
            final JsonNode root = JsonUtil.getMapper().readTree(response.body());
            root.properties().forEach(entry -> {
                final OptionalLong creationDate = toLong(
                        entry.getValue().path("settings").path("index").path("creation_date"));
                if (creationDate.isPresent()) {
                    creationDates.put(entry.getKey(), creationDate.getAsLong());
                }
            });
        } catch (final JacksonException e) {
            LOGGER.debug("Unable to parse index creation dates for '{}': {}", indexName, e.getMessage());
            return Map.of();
        }
        return creationDates;
    }

    // The newest candidate by creation date, a data stream dated by its newest backing index. By name
    // order instead if any candidate has no date.
    private String newest(final Map<String, List<String>> candidates, final Map<String, Long> creationDates) {
        final Map<String, Long> candidateDates = new HashMap<>();
        candidates.forEach((name, indices) -> indices.stream()
                .map(creationDates::get)
                .filter(Objects::nonNull)
                .max(Long::compare)
                .ifPresent(date -> candidateDates.put(name, date)));

        final Comparator<String> byName = Comparator.naturalOrder();
        final Comparator<String> order = candidateDates.size() == candidates.size()
                ? Comparator.<String, Long>comparing(candidateDates::get).thenComparing(byName)
                : byName;
        return candidates.keySet()
                .stream()
                .max(order)
                .orElse(null);
    }

    private IndexTemplate readIndexTemplate(final String templateTarget, final ElasticGet elasticGet)
            throws IOException {
        final ElasticGetResponse response = elasticGet.get("/_index_template");
        if (!response.isSuccess()) {
            return IndexTemplate.failed(statusOf(response), errorMessage(response));
        }

        final List<Template> matching = new ArrayList<>();
        try {
            final JsonNode root = JsonUtil.getMapper().readTree(response.body());
            for (final JsonNode entry : root.path("index_templates")) {
                final String name = stringOrNull(entry.path("name"));
                final JsonNode body = entry.path("index_template");
                final List<String> indexPatterns = strings(body.path("index_patterns"));
                if (name != null
                    && indexPatterns.stream().anyMatch(pattern -> matchesIndexPattern(pattern, templateTarget))) {
                    matching.add(new Template(name, indexPatterns, toLong(body.path("priority")).orElse(0), body));
                }
            }
        } catch (final JacksonException e) {
            return IndexTemplate.failed(Status.ERROR, unreadableMessage("_index_template", e));
        }

        if (matching.isEmpty()) {
            return IndexTemplate.failed(
                    Status.NOT_FOUND,
                    LogUtil.message("No index template matches '{}'", templateTarget));
        }

        // Elasticsearch refuses two overlapping templates of the same priority, so the name only breaks a
        // tie it would not allow, to keep the answer stable
        matching.sort(Comparator.comparingLong(Template::priority)
                .reversed()
                .thenComparing(Template::name));
        final Template applied = matching.getFirst();
        final List<OverlappingTemplate> overlapping = matching.subList(1, matching.size())
                .stream()
                .map(template -> new OverlappingTemplate(
                        template.name(),
                        template.indexPatterns(),
                        template.priority()))
                .toList();

        return new IndexTemplate(
                Status.OK,
                applied.name(),
                toJson(applied.body()),
                overlapping,
                null);
    }

    private List<ComponentTemplate> readComponentTemplates(final String indexTemplateBody,
                                                           final ElasticGet elasticGet) throws IOException {
        final List<String> names = strings(JsonUtil.getMapper().readTree(indexTemplateBody).path("composed_of"));
        final List<ComponentTemplate> componentTemplates = new ArrayList<>(names.size());
        for (final String name : names) {
            componentTemplates.add(readComponentTemplate(name, elasticGet));
        }
        return componentTemplates;
    }

    // One call per name, as the API takes a single name or a wildcard, not a list
    private ComponentTemplate readComponentTemplate(final String name, final ElasticGet elasticGet)
            throws IOException {
        final ElasticGetResponse response = elasticGet.get("/_component_template/" + encode(name));
        if (!response.isSuccess()) {
            return new ComponentTemplate(name, statusOf(response), null, errorMessage(response));
        }

        try {
            final JsonNode root = JsonUtil.getMapper().readTree(response.body());
            for (final JsonNode entry : root.path("component_templates")) {
                if (name.equals(stringOrNull(entry.path("name")))) {
                    return new ComponentTemplate(name, Status.OK, toJson(entry.path("component_template")), null);
                }
            }
        } catch (final JacksonException e) {
            return new ComponentTemplate(name, Status.ERROR, null, unreadableMessage("_component_template", e));
        }
        return new ComponentTemplate(
                name,
                Status.NOT_FOUND,
                null,
                LogUtil.message("Component template '{}' was not found", name));
    }

    private static Status statusOf(final ElasticGetResponse response) {
        return switch (response.statusCode()) {
            case HTTP_FORBIDDEN -> Status.FORBIDDEN;
            case HTTP_NOT_FOUND -> Status.NOT_FOUND;
            default -> Status.ERROR;
        };
    }

    // Elasticsearch's own reason, whole, as for want of privileges it names the privilege
    private static String errorMessage(final ElasticGetResponse response) {
        final String body = response.body();
        if (NullSafe.isNonBlankString(body)) {
            try {
                final JsonNode error = JsonUtil.getMapper().readTree(body).path("error");
                if (error.isString()) {
                    return error.asString();
                }
                final String reason = stringOrNull(error.path("reason"));
                if (reason != null) {
                    return reason;
                }
            } catch (final JacksonException e) {
                LOGGER.debug("Elasticsearch error body is not JSON: {}", e.getMessage());
            }
            return body;
        }
        return LogUtil.message("Elasticsearch returned HTTP {}", response.statusCode());
    }

    private static String unreadableMessage(final String api, final JacksonException e) {
        return LogUtil.message("Unable to read the {} response from Elasticsearch: {}", api, e.getOriginalMessage());
    }

    private static String toJson(final JsonNode node) {
        return JsonUtil.getNoIndentMapper().writeValueAsString(node);
    }

    // Index, alias, data stream and template names are all safe in a path once encoded, and '*' is left
    // as it is, so a wildcard stays a wildcard
    private static String encode(final String pathPart) {
        return URLEncoder.encode(pathPart, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stringOrNull(final JsonNode node) {
        return node.isString()
                ? node.asString()
                : null;
    }

    private static List<String> strings(final JsonNode array) {
        final List<String> strings = new ArrayList<>();
        array.forEach(node -> addIfNamed(strings, node));
        return strings;
    }

    private static void addIfNamed(final List<String> names, final JsonNode node) {
        final String name = stringOrNull(node);
        if (name != null) {
            addIfAbsent(names, name);
        }
    }

    private static void addIfAbsent(final List<String> names, final String name) {
        if (!names.contains(name)) {
            names.add(name);
        }
    }

    private static boolean isBackingIndexOf(final Map<String, List<String>> candidates, final String index) {
        return candidates.entrySet()
                .stream()
                .anyMatch(entry -> !entry.getKey().equals(index) && entry.getValue().contains(index));
    }

    // Elasticsearch gives numbers such as creation_date as strings
    private static OptionalLong toLong(final JsonNode node) {
        if (node.isNumber()) {
            return OptionalLong.of(node.asLong());
        } else if (node.isString()) {
            try {
                return OptionalLong.of(Long.parseLong(node.asString().trim()));
            } catch (final NumberFormatException e) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.empty();
    }

    private record Resolved(String name, String message) {

    }

    private record Template(String name, List<String> indexPatterns, long priority, JsonNode body) {

    }
}

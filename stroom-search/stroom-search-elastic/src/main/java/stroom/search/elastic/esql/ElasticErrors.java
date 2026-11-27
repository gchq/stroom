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

package stroom.search.elastic.esql;

import stroom.util.shared.NullSafe;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.json.JsonData;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Sorts the Elasticsearch errors a query or search can meet that call for advice of their own, for
/// [ElasticEsqlService] and [ElasticEqlService]: the cluster short of memory or overloaded right now (the same query
/// may work shortly), or a query that needs more memory than Elasticsearch allows it (it must ask for less).
///
/// A circuit breaker's error (`circuit_breaking_exception`) says whether it may pass (`durability`: `TRANSIENT`) or
/// not (`PERMANENT`), which the Java client keeps in the error's metadata. The request breaker (`[request]`) and EQL's
/// sequence breaker (`[eql_sequence]`) count one request's own memory, so their trip means the query is too big
/// whatever their durability. A breaker's error without a durability is taken as transient when Elasticsearch
/// answered 429 (as it does for a transient breaker), else as the query being too big. A rejected execution
/// (`es_rejected_execution_exception`), or any other 429, is the cluster overloaded.
///
/// The Java client gives most errors as an [ElasticsearchException], whose error it has read; but an HTTP 500 (a
/// permanent breaker's status) reaches the caller as the low-level client's failure, with the body only in its
/// message, which is read here.
final class ElasticErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CIRCUIT_BREAKING = "circuit_breaking_exception";
    private static final String REJECTED_EXECUTION = "es_rejected_execution_exception";
    private static final String PERMANENT = "PERMANENT";
    private static final String TRANSIENT = "TRANSIENT";
    private static final int TOO_MANY_REQUESTS = 429;
    // The breakers that count one request's own memory.
    private static final Set<String> PER_REQUEST_BREAKERS = Set.of("request", "eql_sequence");
    // The breaker's name, at the start of its reason, e.g. "[parent] Data too large, ...".
    private static final Pattern BREAKER_NAME = Pattern.compile("^\\s*\\[([^\\]]+)]");
    // An error's body in a failure's message: from the first '{"error"'.
    private static final Pattern ERROR_BODY = Pattern.compile("\\{\\s*\"error\"");
    // The deepest cause chain read.
    private static final int MAX_DEPTH = 10;

    private ElasticErrors() {
        // Static only.
    }

    /// Sorts a failure of a request to Elasticsearch.
    ///
    /// @param failure What the Java client threw.
    /// @return What kind of error it is, with the reason to quote (the breaker's or the rejection's own, if
    /// found); [Kind#OTHER] with a null reason for any other failure.
    static Classified classify(final Throwable failure) {
        if (failure instanceof final ElasticsearchException e) {
            final List<Cause> causes = new ArrayList<>();
            collect(e.error(), causes, 0);
            return classify(causes, e.status());
        }
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth < MAX_DEPTH; cause = cause.getCause(), depth++) {
            final JsonNode body = errorBody(cause.getMessage());
            if (body != null) {
                final List<Cause> causes = new ArrayList<>();
                collect(body.path("error"), causes, 0);
                return classify(causes, body.path("status").asInt(0));
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return new Classified(Kind.OTHER, null);
    }

    /// @param reason A circuit breaker's reason.
    /// @return The breaker named at its start, e.g. `parent`, `request` or `eql_sequence`; null if it names none.
    static String breaker(final String reason) {
        final Matcher matcher = BREAKER_NAME.matcher(NullSafe.string(reason));
        return matcher.find()
                ? matcher.group(1).strip()
                : null;
    }

    private static Classified classify(final List<Cause> causes, final int status) {
        for (final Cause cause : causes) {
            if (CIRCUIT_BREAKING.equals(cause.type())) {
                return new Classified(breakerKind(cause, status), cause.reason());
            }
        }
        for (final Cause cause : causes) {
            if (REJECTED_EXECUTION.equals(cause.type())) {
                return new Classified(Kind.OVERLOADED, cause.reason());
            }
        }
        if (status == TOO_MANY_REQUESTS) {
            return new Classified(Kind.OVERLOADED, null);
        }
        return new Classified(Kind.OTHER, null);
    }

    private static Kind breakerKind(final Cause cause, final int status) {
        if (PER_REQUEST_BREAKERS.contains(breaker(cause.reason()))) {
            return Kind.TOO_MUCH_MEMORY;
        }
        if (PERMANENT.equals(cause.durability())) {
            return Kind.TOO_MUCH_MEMORY;
        } else if (TRANSIENT.equals(cause.durability())) {
            return Kind.SHORT_OF_MEMORY;
        }
        return status == TOO_MANY_REQUESTS
                ? Kind.SHORT_OF_MEMORY
                : Kind.TOO_MUCH_MEMORY;
    }

    // The error and its causes, as the client read them: its root causes, what caused it, and what it suppressed.
    private static void collect(final ErrorCause cause, final List<Cause> causes, final int depth) {
        if (cause == null || depth > MAX_DEPTH) {
            return;
        }
        causes.add(new Cause(cause.type(), cause.reason(), durability(cause)));
        for (final ErrorCause root : NullSafe.list(cause.rootCause())) {
            collect(root, causes, depth + 1);
        }
        collect(cause.causedBy(), causes, depth + 1);
        for (final ErrorCause suppressed : NullSafe.list(cause.suppressed())) {
            collect(suppressed, causes, depth + 1);
        }
    }

    // The same, from the error's JSON.
    private static void collect(final JsonNode cause, final List<Cause> causes, final int depth) {
        if (cause == null || !cause.isObject() || depth > MAX_DEPTH) {
            return;
        }
        causes.add(new Cause(text(cause.path("type")), text(cause.path("reason")),
                upper(text(cause.path("durability")))));
        for (final JsonNode root : cause.path("root_cause")) {
            collect(root, causes, depth + 1);
        }
        collect(cause.path("caused_by"), causes, depth + 1);
        for (final JsonNode suppressed : cause.path("suppressed")) {
            collect(suppressed, causes, depth + 1);
        }
    }

    // The error body a failure's message ends with (the low-level client's "method [POST], ..., status line [500]"
    // then the body), or null if it has none.
    private static JsonNode errorBody(final String message) {
        final Matcher matcher = ERROR_BODY.matcher(NullSafe.string(message));
        if (!matcher.find()) {
            return null;
        }
        try {
            final JsonNode body = MAPPER.readTree(message.substring(matcher.start()));
            return body != null && body.path("error").isObject()
                    ? body
                    : null;
        } catch (final JacksonException e) {
            return null;
        }
    }

    // An error's `durability`, upper case (`TRANSIENT` or `PERMANENT`), or null if it has none.
    private static String durability(final ErrorCause cause) {
        final JsonData data = NullSafe.map(cause.metadata()).get("durability");
        if (data == null) {
            return null;
        }
        final JsonValue json = data.toJson();
        return upper(json instanceof final JsonString string
                ? string.getString()
                : String.valueOf(json));
    }

    private static String text(final JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                ? null
                : node.asString();
    }

    private static String upper(final String text) {
        return NullSafe.isBlankString(text)
                ? null
                : text.strip().toUpperCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    /// The kinds of error with advice of their own.
    enum Kind {
        /// A transient circuit breaker: the cluster is short of memory right now.
        SHORT_OF_MEMORY,
        /// A rejected execution, or another 429: the cluster is overloaded right now.
        OVERLOADED,
        /// A permanent circuit breaker, or one counting the request's own memory: the query is too big.
        TOO_MUCH_MEMORY,
        /// Any other error.
        OTHER
    }


    // --------------------------------------------------------------------------------


    /// An error, sorted.
    ///
    /// @param kind   What kind of error it is.
    /// @param reason The reason to quote, or null to quote the error's own.
    record Classified(Kind kind, String reason) {

    }


    // --------------------------------------------------------------------------------


    // One error or cause, as far as sorting needs it.
    private record Cause(String type, String reason, String durability) {

    }
}

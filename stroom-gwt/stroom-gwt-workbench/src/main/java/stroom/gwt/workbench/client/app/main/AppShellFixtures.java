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


package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/// The `App/Main/AppShell` stories' fixtures that are plain Java (so unit tested on the JVM): the
/// React stories' explorer trees and the server's replies that are computed from a request, e.g.
/// the explorer's permissions for the nodes asked about.
final class AppShellFixtures {

    /// The permissions of a node the user owns (React's `crudFixture`).
    static final List<String> FULL_PERMISSIONS = List.of("OWNER", "DELETE", "EDIT", "VIEW", "USE");

    /// The permissions of a node the user may only view (React's `readOnlyCrud`).
    static final List<String> VIEW_ONLY = List.of("VIEW");

    private AppShellFixtures() {
        // Static utility
    }

    /// React's `DICT_TREE`: a 'Dictionaries' root folder holding two dictionaries and a feed, with
    /// React's uuids.
    ///
    /// @return A new fixture of the tree.
    static ExplorerFixture dictTree() {
        return new ExplorerFixture(dictionaries());
    }

    /// React's `ROOTED_TREE`: the Favourites and System roots, with [#dictTree()]'s folder in System.
    /// Differs from React: the roots have Stroom's uuids (`1` and `0`), by which Stroom knows them.
    ///
    /// @return A new fixture of the tree.
    static ExplorerFixture rootedTree() {
        return new ExplorerFixture(
                ExplorerFixture.folder("Favourites", "Favourites").withUuid("1"),
                ExplorerFixture.folder("System", "System", dictionaries()).withUuid("0"));
    }

    private static ExplorerFixture.Node dictionaries() {
        return ExplorerFixture.folder("Dictionaries",
                        ExplorerFixture.doc("Countries", "Dictionary").withUuid("dict-countries"),
                        ExplorerFixture.doc("Colours", "Dictionary").withUuid("dict-colours"),
                        ExplorerFixture.doc("EVENTS", "Feed").withUuid("feed-events"))
                .withUuid("folder-root");
    }

    /// Answers `POST /explorer/v2/fetchExplorerPermissions` as the server does: the permissions of
    /// each node asked about, with the node as it was sent (the client looks them up by node).
    ///
    /// @param requestJson         The request's body, a list of explorer nodes.
    /// @param documentPermissions The user's permissions on each node.
    /// @param createPermissions   The types the user may create in each node.
    /// @return The reply's body, a list of `ExplorerNodePermissions`.
    static String explorerPermissions(final String requestJson,
                                      final List<String> documentPermissions,
                                      final List<String> createPermissions) {
        final StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (final Object node : (List<?>) JsonValues.parse(requestJson)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"explorerNode\":").append(toJson(node))
                    .append(",\"documentPermissions\":").append(toJson(documentPermissions))
                    .append(",\"createPermissions\":").append(toJson(createPermissions))
                    .append(",\"admin\":false}");
        }
        return sb.append(']').toString();
    }

    /// Answers `POST /explorer/v2/decorate` as the server does: the document's reference with its
    /// name (e.g. for a deep link that only gives the type and uuid).
    ///
    /// @param namesByUuid The documents' names by their uuids.
    /// @param requestJson The request's body, a `DecorateRequest`.
    /// @return The reply's body, a `DocRef`.
    static String decorate(final Map<String, String> namesByUuid, final String requestJson) {
        final Map<?, ?> docRef = (Map<?, ?>) ((Map<?, ?>) JsonValues.parse(requestJson)).get("docRef");
        final Object uuid = docRef.get("uuid");
        final Object name = namesByUuid.containsKey(String.valueOf(uuid))
                ? namesByUuid.get(String.valueOf(uuid))
                : docRef.get("name");
        return "{\"type\":" + toJson(docRef.get("type"))
               + ",\"uuid\":" + toJson(uuid)
               + ",\"name\":" + toJson(name) + "}";
    }

    /// Answers `DELETE /explorer/v2/delete` as React's `crudFixture` does: every document asked to
    /// be deleted is reported deleted (so the shell closes their tabs).
    ///
    /// @param requestJson The request's body, an `ExplorerServiceDeleteRequest`.
    /// @return The reply's body, a `BulkActionResult`.
    static String deleted(final String requestJson) {
        final Object docRefs = ((Map<?, ?>) JsonValues.parse(requestJson)).get("docRefs");
        return "{\"explorerNodes\":" + toJson(docRefs) + ",\"message\":\"\"}";
    }

    /// Answers `POST /explorer/v2/create` as React's `crudFixture` does: a new document (uuid
    /// `new-doc`) of the type and with the name asked for, in the destination folder's tree, as the
    /// server's node (with its root, key and flags).
    ///
    /// @param requestJson The request's body, an `ExplorerServiceCreateRequest`.
    /// @return The reply's body, an `ExplorerNode`.
    static String created(final String requestJson) {
        final Map<?, ?> request = (Map<?, ?>) JsonValues.parse(requestJson);
        final Object folder = request.get("destinationFolder");
        final Object root = folder instanceof Map
                ? ((Map<?, ?>) folder).get("rootNodeUuid")
                : null;
        final String type = toJson(request.get("docType"));
        return "{\"type\":" + type
               + ",\"uuid\":\"new-doc\""
               + ",\"name\":" + toJson(request.get("docName"))
               + ",\"rootNodeUuid\":" + toJson(root)
               + ",\"uniqueKey\":{\"type\":" + type + ",\"uuid\":\"new-doc\",\"rootNodeUuid\":" + toJson(root) + "}"
               + ",\"nodeFlags\":[\"L\"]}";
    }

    /// Answers `PUT /explorer/v2/rename` as React's `crudFixture` does: the node with its new name.
    ///
    /// @param requestJson The request's body, an `ExplorerServiceRenameRequest`.
    /// @return The reply's body, an `ExplorerNode`.
    static String renamed(final String requestJson) {
        final Map<?, ?> request = (Map<?, ?>) JsonValues.parse(requestJson);
        final Map<Object, Object> node = new LinkedHashMap<>((Map<?, ?>) request.get("explorerNode"));
        node.put("name", request.get("docName"));
        return toJson(node);
    }

    /// Writes a value read by [JsonValues#parse(String)] (maps, lists, strings, numbers, booleans and
    /// null) as JSON.
    ///
    /// @param value The value.
    /// @return The JSON.
    static String toJson(final Object value) {
        final StringBuilder sb = new StringBuilder();
        append(sb, value);
        return sb.toString();
    }

    private static void append(final StringBuilder sb, final Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            appendString(sb, (String) value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (final Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                appendString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                append(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (final Object item : (Collection<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                append(sb, item);
            }
            sb.append(']');
        } else if (value instanceof BigDecimal) {
            sb.append(((BigDecimal) value).toPlainString());
        } else {
            sb.append(value);
        }
    }

    private static void appendString(final StringBuilder sb, final String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        final String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int j = hex.length(); j < 4; j++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}

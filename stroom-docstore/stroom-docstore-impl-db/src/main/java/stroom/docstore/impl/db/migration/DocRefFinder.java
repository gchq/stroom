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

package stroom.docstore.impl.db.migration;

import stroom.docref.DocRef;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * Utility for recursively walking a JSON tree to find DocRef-shaped objects.
 * A DocRef is identified by the presence of both {@code type} (String) and
 * {@code uuid} (String) fields on a JSON object.
 * <p>
 * Used by the doc_dependency Flyway migrations to extract dependencies
 * from serialised document JSON without needing to deserialise into
 * specific domain classes.
 */
public final class DocRefFinder {

    private DocRefFinder() {
        // Utility class
    }

    /**
     * Recursively walks the JSON tree looking for objects that have both
     * a {@code type} and {@code uuid} string field (the signature of a DocRef).
     * Self-references (where uuid matches the owning entity's uuid) are excluded.
     *
     * @param node      the JSON node to walk
     * @param ownerUuid the UUID of the owning entity (to exclude self-references)
     * @param results   the set to add discovered DocRef holders to
     */
    public static void findDocRefs(final JsonNode node,
                                   final String ownerUuid,
                                   final Set<DocRef> results) {
        if (node == null) {
            return;
        }

        if (node.isObject()) {
            // Check if this object looks like a DocRef
            final JsonNode typeNode = node.get("type");
            final JsonNode uuidNode = node.get("uuid");

            if (typeNode != null && typeNode.isTextual()
                && uuidNode != null && uuidNode.isTextual()) {
                final String type = typeNode.asText();
                final String uuid = uuidNode.asText();

                // Only include if it looks like a real DocRef (non-blank type and uuid)
                // and is not a self-reference to the owning document
                if (!type.isBlank() && !uuid.isBlank() && !uuid.equals(ownerUuid)) {
                    final JsonNode nameNode = node.get("name");
                    final String name = (nameNode != null && nameNode.isTextual())
                            ? nameNode.asText()
                            : "";
                    results.add(new DocRef(type, uuid, name));
                }
            }

            // Recurse into all child properties regardless (DocRefs can be nested
            // inside other DocRef-like objects, e.g. expression trees)
            final Set<Map.Entry<String, JsonNode>> properties = node.properties();
            for (final Map.Entry<String, JsonNode> entry : properties) {
                findDocRefs(entry.getValue(), ownerUuid, results);
            }

        } else if (node.isArray()) {
            for (final JsonNode element : node) {
                findDocRefs(element, ownerUuid, results);
            }
        }
    }

    /**
     * Returns the input string, or empty string if null.
     */
    public static String safeStr(final String value) {
        return value != null
                ? value
                : "";
    }
}

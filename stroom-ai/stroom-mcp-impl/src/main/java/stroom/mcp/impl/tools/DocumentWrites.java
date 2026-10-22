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
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Document;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Documents read and written through Stroom's stores as the user (who needs View to read, Edit to write), for the
/// builds' tools: a build's Documentation docs, and a working copy's content written back over its original (the
/// Stroom MCP server's `get_doc` and `put_doc`). A document of any type is handled as its JSON, so the fields the
/// Stroom MCP server copied (`data`, `converterType`, `documentation`, `description`) are copied whatever its type.
class DocumentWrites {

    private final DocumentAccess documentAccess;

    /// @param documentAccess Reads documents (checking View) and gives each type's store.
    @Inject
    DocumentWrites(final DocumentAccess documentAccess) {
        this.documentAccess = documentAccess;
    }

    /// @param ref A Documentation doc.
    /// @return It.
    /// @throws ToolRefusal if it can't be read.
    DocumentationDoc documentation(final DocRef ref) {
        return documentAccess.read(new DocRef(DocumentationDoc.TYPE, ref.getUuid(), ref.getName()),
                        DocumentationDoc.class)
                .orElseThrow(() -> new ToolRefusal("The Documentation doc " + ref.getUuid() + " can't be read: it "
                                                   + "doesn't exist, or you don't have permission to view it"));
    }

    /// @param ref A Documentation doc.
    /// @return Its text ([BuildRecords#bodyText]).
    /// @throws ToolRefusal if it can't be read.
    String text(final DocRef ref) {
        return BuildRecords.bodyText(documentation(ref));
    }

    /// @param ref  A Documentation doc.
    /// @param text Its new text.
    /// @return It as saved.
    /// @throws ToolRefusal if it can't be read.
    DocumentationDoc writeText(final DocRef ref, final String text) {
        return write(BuildRecords.withBodyText(documentation(ref), text));
    }

    /// @param doc A Documentation doc, changed.
    /// @return It as saved.
    @SuppressWarnings("unchecked")
    DocumentationDoc write(final DocumentationDoc doc) {
        final DocumentActionHandler<DocumentationDoc> handler = documentAccess.handler(DocumentationDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no Documentation store"));
        return handler.writeDocument(doc);
    }

    /// @param ref A document of any type.
    /// @return It, as its JSON.
    /// @throws ToolRefusal if it can't be read.
    Map<String, Object> fields(final DocRef ref) {
        return asMap(read(ref));
    }

    /// Writes some of one document's fields over another's (a working copy's over its original), as the user.
    ///
    /// @param from The document whose fields are taken.
    /// @param to   The document written.
    /// @param keys The fields, as the documents' JSON names them; those `from` doesn't have are left as they are.
    /// @throws ToolRefusal if either can't be read, or the type has no store.
    @SuppressWarnings({"unchecked", "rawtypes"})
    void copyFields(final DocRef from, final DocRef to, final List<String> keys) {
        final Map<String, Object> source = fields(from);
        final Object target = read(to);
        final Map<String, Object> written = asMap(target);
        for (final String key : keys) {
            if (source.containsKey(key)) {
                written.put(key, source.get(key));
            }
        }
        final DocumentActionHandler handler = documentAccess.handler(to.getType())
                .orElseThrow(() -> new ToolRefusal("Stroom has no " + to.getType() + " store"));
        final Class<?> type = target.getClass();
        final Document changed = (Document) JsonUtil.readValue(JsonUtil.writeValueAsString(written, false), type);
        handler.writeDocument(changed);
    }

    /// @param one   A document, as its JSON.
    /// @param other Another, as its JSON.
    /// @param keys  The fields compared.
    /// @return True if every one of the fields is the same in both.
    static boolean same(final Map<String, Object> one, final Map<String, Object> other, final List<String> keys) {
        return keys.stream().allMatch(key -> Objects.equals(one.get(key), other.get(key)));
    }

    private Object read(final DocRef ref) {
        if (!documentAccess.canView(ref)) {
            throw new ToolRefusal(unreadable(ref));
        }
        return documentAccess.read(ref).orElseThrow(() -> new ToolRefusal(unreadable(ref)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object doc) {
        final Map<String, Object> map = JsonUtil.readValue(JsonUtil.writeValueAsString(doc, false), Map.class);
        return map == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(map);
    }

    private static String unreadable(final DocRef ref) {
        return ref.getType() + " " + ref.getUuid() + " can't be read: it doesn't exist, or you don't have "
               + "permission to view it";
    }
}

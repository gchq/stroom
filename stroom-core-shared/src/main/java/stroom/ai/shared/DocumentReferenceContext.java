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

package stroom.ai.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Stroom documents the user attached to a chat from the Explorer tree ("Attach to chat"). Each becomes an
/// attachment of its own, which the chat refers to by its uuid.
@JsonInclude(Include.NON_NULL)
public final class DocumentReferenceContext extends AskStroomAiContext {

    @JsonProperty
    private final List<DocumentReference> documents;

    /// @param documents The documents attached.
    @JsonCreator
    public DocumentReferenceContext(@JsonProperty("documents") final List<DocumentReference> documents) {
        this.documents = documents == null
                ? Collections.emptyList()
                : new ArrayList<>(documents);
    }

    /// @return The documents attached.
    public List<DocumentReference> getDocuments() {
        return documents;
    }

    /// @return What is attached, e.g. "Dictionary 'Malicious IPs'", or how many documents for several.
    @JsonIgnore
    @Override
    public String getDescription() {
        if (documents.size() == 1) {
            return describe(documents.get(0).getDocRef());
        }
        return documents.size() + " documents";
    }

    /// @param docRef A document.
    /// @return What it is, e.g. "Dictionary 'Malicious IPs'".
    public static String describe(final DocRef docRef) {
        if (docRef == null) {
            return "Document";
        }
        final String type = docRef.getType() == null || docRef.getType().isEmpty()
                ? "Document"
                : docRef.getType();
        final String name = docRef.getName() == null || docRef.getName().isEmpty()
                ? docRef.getUuid()
                : docRef.getName();
        return type + " '" + name + "'";
    }
}

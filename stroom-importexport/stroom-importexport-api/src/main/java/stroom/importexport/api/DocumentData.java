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

package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.Base64EncodedDocumentData;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Schema(description = "Raw data representation of a document")
public class DocumentData implements Serializable {

    @Schema(description = "The document reference for the document",
            required = true)
    private DocRef docRef;

    @Schema(description = "A map of file extensions to file contents that are used to represent all of the document " +
                          "contents",
            required = true)
    private Map<String, byte[]> dataMap;

    public DocumentData() {
    }

    public DocumentData(final DocRef docRef, final Map<String, byte[]> dataMap) {
        this.docRef = docRef;
        this.dataMap = dataMap;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    public Map<String, byte[]> getDataMap() {
        return dataMap;
    }

    public void setDataMap(final Map<String, byte[]> dataMap) {
        this.dataMap = dataMap;
    }

    public static Base64EncodedDocumentData toBase64EncodedDocumentData(final DocumentData documentData) {
        final Map<String, String> encodedData = documentData.getDataMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> Base64.getEncoder().encodeToString(e.getValue())));
        return new Base64EncodedDocumentData(documentData.getDocRef(), encodedData);
    }

    public static DocumentData fromBase64EncodedDocumentData(
            final Base64EncodedDocumentData base64EncodedDocumentData) {

        final Map<String, byte[]> decodedData = base64EncodedDocumentData.getDataMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, e -> Base64.getDecoder().decode(e.getValue())));
        return new DocumentData(base64EncodedDocumentData.getDocRef(), decodedData);
    }
}

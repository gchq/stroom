/*
 * Copyright 2017 Crown Copyright
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

package stroom.docstore.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Document;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DocRefUtil {

    private DocRefUtil() {
        // Utility class.
    }

    public static DocRef create(final AbstractDoc doc) {
        return doc != null
                ? new DocRef(doc.getType(), doc.getUuid(), doc.getName())
                : null;
    }

    public static <T extends Document> Map<DocRef, T> toMapByDocRef(final Collection<T> documents) {
        return NullSafe.stream(documents)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Document::asDocRef,
                        Function.identity()));
    }

    public static <T extends Document> Map<DocRef, T> toMapByDocRef(final T... documents) {
        return NullSafe.stream(documents)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Document::asDocRef,
                        Function.identity()));
    }

    public static <T extends Document> Map<String, T> toMapByUuid(final Collection<T> documents) {
        return NullSafe.stream(documents)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        (Document document) -> document.asDocRef().getUuid(),
                        Function.identity()));
    }

    public static <T extends Document> Map<String, T> toMapByUuid(final T... documents) {
        return NullSafe.stream(documents)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        (Document document) -> document.asDocRef().getUuid(),
                        Function.identity()));
    }

    /**
     * Return true if the docRef and doc have the same UUID and type.
     * If either is null, returns false.
     */
    public static boolean isSameDocument(final AbstractDoc doc, final DocRef docRef) {
        return isSameDocument(docRef, doc);
    }

    /**
     * Return true if the docRef and doc have the same UUID and type.
     * If either is null, returns false.
     */
    public static boolean isSameDocument(final DocRef docRef, final AbstractDoc doc) {
        if (docRef == null || doc == null) {
            return false;
        } else {
            return Objects.equals(docRef.getUuid(), doc.getUuid())
                   && Objects.equals(docRef.getType(), doc.getType());
        }
    }

    public static String createSimpleDocRefString(final DocRef docRef) {
        if (docRef == null) {
            return "";
        } else {
            if (docRef.getName() != null && docRef.getUuid() != null) {
                return docRef.getName() + " {" + docRef.getUuid() + "}";
            } else if (docRef.getName() != null) {
                return docRef.getName();
            } else if (docRef.getUuid() != null) {
                return docRef.getUuid();
            } else {
                return "";
            }
        }
    }

    public static String createTypedDocRefString(final DocRef docRef) {
        if (docRef == null) {
            return "";
        } else {
            if (docRef.getName() != null && docRef.getUuid() != null) {
                return String.join(" ",
                        docRef.getType(),
                        StringUtil.singleQuote(docRef.getName()) + " {" + docRef.getUuid() + "}");
            } else if (docRef.getName() != null) {
                return String.join(" ",
                        docRef.getType(),
                        StringUtil.singleQuote(docRef.getName()));
            } else if (docRef.getUuid() != null) {
                return String.join(" ",
                        docRef.getType(),
                        docRef.getUuid());
            } else {
                return "";
            }
        }
    }
}

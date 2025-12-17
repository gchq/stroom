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

package stroom.query.common.v2;

import stroom.docref.DocRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Deprecated
public class LegacyDocRefConverter {

    private static final Map<DocRef, DocRef> LEGACY_DOC_REF_CONVERSION = new HashMap<>();

    static {
        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Meta Store")
                        .build(),
                DocRef
                        .builder()
                        .type("StreamStore")
                        .uuid("StreamStore")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("StreamStore")
                        .uuid("0")
                        .build(),
                DocRef
                        .builder()
                        .type("StreamStore")
                        .uuid("StreamStore")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Annotations")
                        .build(),
                DocRef
                        .builder()
                        .type("Annotations")
                        .uuid("Annotations")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Index Shards")
                        .build(),
                DocRef
                        .builder()
                        .type("IndexShards")
                        .uuid("IndexShards")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Processor Tasks")
                        .build(),
                DocRef
                        .builder()
                        .type("ProcessorTasks")
                        .uuid("ProcessorTasks")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Reference Data Store")
                        .build(),
                DocRef
                        .builder()
                        .type("ReferenceDataStore")
                        .uuid("ReferenceDataStore")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Dual")
                        .build(),
                DocRef
                        .builder()
                        .type("Dual")
                        .uuid("Dual")
                        .build());

        LEGACY_DOC_REF_CONVERSION.put(
                DocRef
                        .builder()
                        .type("Searchable")
                        .uuid("Task Manager")
                        .build(),
                DocRef
                        .builder()
                        .type("TaskManager")
                        .uuid("TaskManager")
                        .build());
    }

    public static DocRef convert(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        return Objects.requireNonNullElse(LEGACY_DOC_REF_CONVERSION.get(docRef), docRef);
    }
}

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

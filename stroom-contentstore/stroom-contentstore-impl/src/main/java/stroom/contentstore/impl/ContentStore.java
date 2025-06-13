package stroom.contentstore.impl;

import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreMetadata;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class to represent content packs from one URL.
 * Used to pull the content packs in from the YAML App Store file.
 */
public class ContentStore {

    /** Metadata associated with the content store */
    @JsonProperty("meta")
    private final ContentStoreMetadata meta;

    /** List of git repositories */
    @JsonProperty("contentPacks")
    private final List<ContentStoreContentPack> contentPacks = new ArrayList<>();

    /**
     * Constructor. Called from YAML parser.
     * @param meta The metadata associated with the content store.
     *             Must not be null.
     * @param contentPacks The list of content packs in the content store.
     *                     Can be null or empty.
     */
    @SuppressWarnings("unused")
    public ContentStore(@JsonProperty("meta") final ContentStoreMetadata meta,
                        @JsonProperty("contentPacks") final List<ContentStoreContentPack> contentPacks) {
        Objects.requireNonNull(meta);
        this.meta = meta;
        if (contentPacks != null) {
            this.contentPacks.addAll(contentPacks);
        }
    }

    /**
     * @return the metadata associated with this content store.
     * Never returns null.
     */
    public ContentStoreMetadata getMeta() {
        return meta;
    }

    /**
     * @return the content packs available in this content store.
     * Never returns null but may return an empty List.
     * Returned list is unmodifiable.
     */
    public List<ContentStoreContentPack> getContentPacks() {
        return Collections.unmodifiableList(contentPacks);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ContentStore that = (ContentStore) o;
        return Objects.equals(meta, that.meta)
                && Objects.equals(contentPacks, that.contentPacks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                meta,
                contentPacks);
    }

    @Override
    public String toString() {
        return "ContentPacks {"
                + "\n  meta=" + meta
                + "\n  contentPacks =" + contentPacks
                + "\n}";
    }
}

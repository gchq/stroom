package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.util.shared.AbstractBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DocumentData {

    private final DocRef docRef;
    private final String version;
    private final String uniqueName;
    private final Map<String, byte[]> data;

    public DocumentData(final DocRef docRef,
                        final String version,
                        final String uniqueName,
                        final Map<String, byte[]> data) {
        this.docRef = docRef;
        this.version = version;
        this.uniqueName = uniqueName;
        this.data = data;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getVersion() {
        return version;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public Set<String> getEntries() {
        return data.keySet();
    }

    public byte[] getData(final String entry) {
        return data.get(entry);
    }

    public Map<String, byte[]> getData() {
        return data;
    }

    @Override
    public String toString() {
        return docRef.toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<DocumentData, Builder> {

        private DocRef docRef;
        private String version;
        private String uniqueName;
        private Map<String, byte[]> data;

        public Builder() {

        }

        public Builder(final DocumentData documentData) {
            this.docRef = documentData.docRef;
            this.version = documentData.version;
            this.uniqueName = documentData.uniqueName;
            this.data = new HashMap<>(documentData.data);
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return self();
        }

        public Builder version(final String version) {
            this.version = version;
            return self();
        }

        public Builder uniqueName(final String uniqueName) {
            this.uniqueName = uniqueName;
            return self();
        }

        public Builder data(final Map<String, byte[]> data) {
            this.data = data;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public DocumentData build() {
            return new DocumentData(docRef, version, uniqueName, data);
        }
    }
}

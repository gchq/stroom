package stroom.proxy.repo;

import stroom.data.zip.StroomZipFileType;

public class RepoSourceEntry {

    private final long id;
    private final StroomZipFileType type;
    private final String extension;
    private final long byteSize;

    private RepoSourceEntry(final long id,
                            final StroomZipFileType type,
                            final String extension,
                            final long byteSize) {
        this.id = id;
        this.type = type;
        this.extension = extension;
        this.byteSize = byteSize;
    }

    public long getId() {
        return id;
    }

    public StroomZipFileType getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    public long getByteSize() {
        return byteSize;
    }

    @Override
    public String toString() {
        return "RepoSourceEntry{" +
                "id=" + id +
                ", type=" + type +
                ", extension='" + extension + '\'' +
                ", byteSize=" + byteSize +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private long id;
        private StroomZipFileType type;
        private String extension;
        private long byteSize;

        private Builder() {
        }

        private Builder(final RepoSourceEntry entry) {
            type = entry.type;
            extension = entry.extension;
            byteSize = entry.byteSize;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder type(final StroomZipFileType type) {
            this.type = type;
            return this;
        }

        public Builder extension(final String extension) {
            this.extension = extension;
            return this;
        }

        public Builder byteSize(final long byteSize) {
            this.byteSize = byteSize;
            return this;
        }

        public RepoSourceEntry build() {
            return new RepoSourceEntry(id, type, extension, byteSize);
        }
    }
}

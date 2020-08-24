package stroom.proxy.repo;

import java.util.Objects;

public class FileSetKey {
    private final String feedName;
    private final String typeName;

    public FileSetKey(final String feedName,
                      final String typeName) {
        this.feedName = feedName;
        this.typeName = typeName;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final FileSetKey that = (FileSetKey) o;
        return Objects.equals(feedName, that.feedName) &&
                Objects.equals(typeName, that.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feedName, typeName);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (feedName != null) {
            sb.append(feedName);
        }
        if (typeName != null) {
            if (sb.length() > 0) {
                sb.append(" - ");
            }
            sb.append(typeName);
        }
        return sb.toString();
    }
}

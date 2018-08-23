package stroom.refdata.store.offheapstore;

import java.util.Objects;

public class ValueStoreMeta {

    private final int typeId;
    private final int referenceCount;

    public ValueStoreMeta(int typeId) {
        this.typeId = typeId;
        this.referenceCount = 1;
    }

    public ValueStoreMeta(int typeId, int referenceCount) {
        this.typeId = typeId;
        this.referenceCount = referenceCount;
    }

    public int getTypeId() {
        return typeId;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValueStoreMeta that = (ValueStoreMeta) o;
        return typeId == that.typeId &&
                referenceCount == that.referenceCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeId, referenceCount);
    }

    @Override
    public String toString() {
        return "ValueStoreMeta{" +
                "typeId=" + typeId +
                ", referenceCount=" + referenceCount +
                '}';
    }
}

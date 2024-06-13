package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.store.RefDataValue;

import java.util.Objects;

public class StagingRefDataValue {

    private final byte typeId;
    private final RefDataValue refDataValue;

    public StagingRefDataValue(final byte typeId,
                                final RefDataValue refDataValue) {
        // Overrides the typeId that may be in the RefDataValue, e.g. if it is an
        // UnknownRefDataValue
        this.typeId = typeId;
        this.refDataValue = refDataValue;
    }

    public static StagingRefDataValue wrap(final RefDataValue refDataValue) {
        return new StagingRefDataValue(refDataValue.getTypeId(), refDataValue);
    }

    public byte getTypeId() {
        return typeId;
    }

    public RefDataValue getRefDataValue() {
        return refDataValue;
    }

    @Override
    public String toString() {
        return "StagingRefDataValue{" +
                "typeId=" + typeId +
                ", refDataValue=" + refDataValue +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StagingRefDataValue that = (StagingRefDataValue) o;
        return typeId == that.typeId && Objects.equals(refDataValue, that.refDataValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeId, refDataValue);
    }
}

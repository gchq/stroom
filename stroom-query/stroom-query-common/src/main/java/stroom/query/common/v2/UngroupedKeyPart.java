package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Output;

import java.util.Objects;

class UngroupedKeyPart implements KeyPart {
    private long sequenceNumber;

    public UngroupedKeyPart(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setSequenceNumber(final long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public void write(final Output output) {
        output.writeLong(sequenceNumber);
    }

    @Override
    public boolean isGrouped() {
        return false;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UngroupedKeyPart that = (UngroupedKeyPart) o;
        return sequenceNumber == that.sequenceNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber);
    }

    @Override
    public void append(final StringBuilder sb) {
        sb.append("~");
        sb.append(sequenceNumber);
    }

    @Override
    public String toString() {
        return "";
    }
}
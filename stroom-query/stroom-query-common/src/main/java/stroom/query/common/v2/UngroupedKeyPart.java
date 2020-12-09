package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Output;

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
    public void append(final StringBuilder sb) {
        sb.append("~");
        sb.append(sequenceNumber);
    }

    @Override
    public String toString() {
        return "";
    }
}
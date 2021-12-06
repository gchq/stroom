package stroom.query.common.v2;

public class LmdbPayload {

    private final boolean finalPayload;
    private final byte[] data;

    public LmdbPayload(final boolean finalPayload, final byte[] data) {
        this.finalPayload = finalPayload;
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isFinalPayload() {
        return finalPayload;
    }
}

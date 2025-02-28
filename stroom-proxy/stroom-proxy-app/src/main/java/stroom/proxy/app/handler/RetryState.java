package stroom.proxy.app.handler;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

record RetryState(long firstAttemptEpochMs,
                  long lastAttemptEpochMs,
                  int attempts) {

    public static final int TOTAL_BYTES = Long.BYTES + Long.BYTES + Integer.BYTES;

    public static RetryState initial() {
        final long nowMs = System.currentTimeMillis();
        return new RetryState(nowMs, nowMs, 1);
    }

    public RetryState cloneAndUpdate() {
        return new RetryState(
                firstAttemptEpochMs,
                System.currentTimeMillis(),
                attempts + 1);
    }

    static RetryState deserialise(final byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return deserialise(byteBuffer);
    }

    static RetryState deserialise(final ByteBuffer byteBuffer) {
        return new RetryState(
                byteBuffer.getLong(),
                byteBuffer.getLong(),
                byteBuffer.getInt());
    }

    byte[] serialise() {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[TOTAL_BYTES]);
        byteBuffer.putLong(firstAttemptEpochMs);
        byteBuffer.putLong(lastAttemptEpochMs);
        byteBuffer.putInt(attempts);
        return byteBuffer.array();
    }

    void serialise(final ByteBuffer byteBuffer) {
        byteBuffer.putLong(firstAttemptEpochMs);
        byteBuffer.putLong(lastAttemptEpochMs);
        byteBuffer.putInt(attempts);
    }

    Duration getTimeSinceFirstAttempt() {
        return Duration.between(Instant.ofEpochMilli(firstAttemptEpochMs), Instant.now());
    }

    @Override
    public String toString() {
        return "RetryState{" +
               "firstAttemptEpochMs=" + Instant.ofEpochMilli(firstAttemptEpochMs) +
               ", lastAttemptEpochMs=" + Instant.ofEpochMilli(lastAttemptEpochMs) +
               ", attempts=" + attempts +
               '}';
    }
}

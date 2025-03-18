package stroom.proxy.app.handler;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;

record RetryState(long firstAttemptEpochMs,
                  long lastAttemptEpochMs,
                  short attempts) {

    public static final int TOTAL_BYTES = Long.BYTES + Long.BYTES + Short.BYTES;
    public static final int MAX_ATTEMPTS = Short.MAX_VALUE;

    RetryState {
        if (lastAttemptEpochMs < firstAttemptEpochMs) {
            throw new IllegalArgumentException("lastAttemptEpochMs is before firstAttemptEpochMs");
        }
    }

    public static RetryState initial() {
        final long nowMs = System.currentTimeMillis();
        return new RetryState(nowMs, nowMs, (short) 1);
    }

    public RetryState cloneAndUpdate() {
        final short newAttempts = attempts < Short.MAX_VALUE
                ? (short) (attempts + 1)
                : attempts;
        return new RetryState(firstAttemptEpochMs, System.currentTimeMillis(), newAttempts);
    }

    static RetryState deserialise(final byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return deserialise(byteBuffer);
    }

    static RetryState deserialise(final ByteBuffer byteBuffer) {

        return new RetryState(
                byteBuffer.getLong(),
                byteBuffer.getLong(),
                byteBuffer.getShort());
    }

    byte[] serialise() {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[TOTAL_BYTES]);
        serialise(byteBuffer);
        return byteBuffer.array();
    }

    void serialise(final ByteBuffer byteBuffer) {
        byteBuffer.putLong(firstAttemptEpochMs);
        byteBuffer.putLong(lastAttemptEpochMs);
        byteBuffer.putShort(attempts);
    }

    Duration getTimeSinceFirstAttempt() {
        return Duration.between(Instant.ofEpochMilli(firstAttemptEpochMs), Instant.now());
    }

    boolean hasReachMaxAttempts() {
        return attempts == Short.MAX_VALUE;
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

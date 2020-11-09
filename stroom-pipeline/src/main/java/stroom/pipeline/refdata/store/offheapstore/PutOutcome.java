package stroom.pipeline.refdata.store.offheapstore;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a put operation to a map or similar KV store
 */
public class PutOutcome {
    private final boolean isSuccess;
    private final Boolean isDuplicate;

    private PutOutcome(final boolean isSuccess,
                       final Boolean isDuplicate) {
        this.isSuccess = isSuccess;
        this.isDuplicate = isDuplicate;
    }

    public static PutOutcome success() {
        return new PutOutcome(true, null);
    }

    public static PutOutcome newEntry() {
        return new PutOutcome(true, false);
    }

    public static PutOutcome replacedEntry() {
        return new PutOutcome(true, true);
    }

    public static PutOutcome failed() {
        return new PutOutcome(false, true);
    }

    /**
     * @return True if the put happened or the final state is equivalent to the put
     * having happened,
     * i.e. a duplicate key and value to the one being put.
     */
    public boolean isSuccess() {
        return isSuccess;
    }

    /**
     * @return An optional that states whether the entry put was a duplicate of
     * an existing key, if known.
     */
    public Optional<Boolean> isDuplicate() {
        return Optional.ofNullable(isDuplicate);
    }

    @Override
    public String toString() {
        return "PutOutcome{" +
                "isSuccess=" + isSuccess +
                ", isDuplicate=" + isDuplicate +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PutOutcome that = (PutOutcome) o;
        return isSuccess == that.isSuccess &&
                Objects.equals(isDuplicate, that.isDuplicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isSuccess, isDuplicate);
    }
}

package stroom.util.shared;

public interface HasCapacity {

    /**
     * Return the capacity state for this object.
     * Should not return null.
     */
    default HasCapacityInfo getCapacityInfo() {
        return HasCapacityInfo.UNKNOWN;
    }
}

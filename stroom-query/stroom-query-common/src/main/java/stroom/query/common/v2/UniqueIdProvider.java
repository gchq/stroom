package stroom.query.common.v2;

public interface UniqueIdProvider {

    /**
     * Create a unique id.
     *
     * @return A unique id.
     */
    long getUniqueId();
}

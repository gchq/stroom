package stroom.meta.shared;

/**
 * A lightweight abstraction of Meta for when you are only interested in
 * the key bits of meta
 */
public interface SimpleMeta {

    long getId();

    String getTypeName();

    String getFeedName();

    long getCreateMs();

    Long getStatusMs();
}

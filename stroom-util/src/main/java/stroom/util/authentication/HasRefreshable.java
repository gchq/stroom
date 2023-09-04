package stroom.util.authentication;

public interface HasRefreshable<T extends Refreshable> {

    T getRefreshable();
}

package stroom.util.shared;

public interface FetchWithUuid<T> {

    T fetch(String uuid);
}

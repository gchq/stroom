package stroom.util.shared;

public interface FetchWithLongId<T> {

    T fetch(Long id);
}

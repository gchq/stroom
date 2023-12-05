package stroom.proxy.repo.dao.lmdb;

public interface RowKey<T> {
    T next();
}

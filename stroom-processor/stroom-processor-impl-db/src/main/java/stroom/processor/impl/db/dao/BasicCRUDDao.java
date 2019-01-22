package stroom.processor.impl.db.dao;

public interface BasicCRUDDao<T> {

    T create();

    T update(T value);

    int delete(int id);

    T fetch(int id);
}

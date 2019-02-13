package stroom.util.entity;

import java.util.Optional;

public interface BasicCrudDao<T> {

    T create();

    T update(T record);

    int delete(int id);

    Optional<T> fetch(int id);
}

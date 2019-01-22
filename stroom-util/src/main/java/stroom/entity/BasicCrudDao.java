package stroom.entity;

import java.util.Optional;

public interface BasicCrudDao<T> {

    T create();

    T update(final T record);

    int delete(final int id);

    Optional<T> fetch(final int id);
}

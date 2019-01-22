package stroom.entity;

public interface BasicCrudDao<T> {

    T create();

    T update(T record);

    int delete(int id);

    T fetch(int id);
}

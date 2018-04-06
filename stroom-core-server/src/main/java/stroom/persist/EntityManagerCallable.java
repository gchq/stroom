package stroom.persist;

import javax.persistence.EntityManager;

public interface EntityManagerCallable<T> {
    T run(EntityManager entityManager);
}
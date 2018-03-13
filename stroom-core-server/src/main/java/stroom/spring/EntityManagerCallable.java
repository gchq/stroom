package stroom.spring;

import javax.persistence.EntityManager;

public interface EntityManagerCallable<T> {
    T run(EntityManager entityManager);
}
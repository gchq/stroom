package stroom.spring;

import javax.persistence.EntityManager;

public interface TransactionalCallable<T> {
    T run(EntityManager entityManager) throws Exception;
}
package stroom.spring;

import javax.persistence.EntityManager;

public interface TransactionalRunnable {
    void run(EntityManager entityManager);
}
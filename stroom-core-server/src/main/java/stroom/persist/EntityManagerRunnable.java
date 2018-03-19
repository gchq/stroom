package stroom.persist;

import javax.persistence.EntityManager;

public interface EntityManagerRunnable {
    void run(EntityManager entityManager);
}
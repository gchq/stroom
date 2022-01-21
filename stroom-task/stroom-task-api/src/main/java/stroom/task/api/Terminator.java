package stroom.task.api;

public interface Terminator {
    Terminator DEFAULT = new Terminator() {};

    default boolean isTerminated() {
        return false;
    }

    default void checkTermination() throws TaskTerminatedException {
    }
}

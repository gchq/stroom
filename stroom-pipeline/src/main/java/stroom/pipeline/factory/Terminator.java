package stroom.pipeline.factory;

public interface Terminator {
    Terminator DEFAULT = () -> {
    };

    void check();
}

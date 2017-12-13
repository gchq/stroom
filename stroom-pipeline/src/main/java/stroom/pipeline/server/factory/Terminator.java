package stroom.pipeline.server.factory;

public interface Terminator {
    Terminator DEFAULT = () -> {
    };

    void check();
}

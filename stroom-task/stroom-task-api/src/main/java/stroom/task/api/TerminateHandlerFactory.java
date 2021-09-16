package stroom.task.api;

public interface TerminateHandlerFactory {

    TerminateHandler NOOP_HANDLER = () -> {
    };
    TerminateHandlerFactory NOOP_FACTORY = () -> NOOP_HANDLER;

    TerminateHandler create();
}

package stroom.dispatch.client;

public interface RestFactory {
    <R> Rest<R> create();
}

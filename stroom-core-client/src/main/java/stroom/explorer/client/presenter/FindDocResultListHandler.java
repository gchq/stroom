package stroom.explorer.client.presenter;

public interface FindDocResultListHandler<T> {

    void openDocument(T match);

    void focus();
}

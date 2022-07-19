package stroom.proxy.repo;

import stroom.receive.common.StreamHandler;

public interface Sender {

    /**
     * Send specific repo source entries to a handler.
     *
     * @param items   The entries to send.
     * @param handler The handler to receive the entries.
     */
    void sendDataToHandler(Items items,
                           StreamHandler handler);

    /**
     * Send a single source to a handler.
     *
     * @param source  The source to send.
     * @param handler The handler to receive the source.
     */
    void sendDataToHandler(RepoSource source,
                           StreamHandler handler);
}

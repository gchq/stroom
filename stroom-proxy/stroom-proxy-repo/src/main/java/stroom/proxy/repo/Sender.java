package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.receive.common.StreamHandler;

import java.util.List;

public interface Sender {

    /**
     * Send specific repo source entries to a handler.
     *
     * @param attributeMap The attributes to send to the handler.
     * @param items        The entries to send.
     * @param handler      The handler to receive the entries.
     */
    void sendDataToHandler(AttributeMap attributeMap,
                           List<SourceItems> items,
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

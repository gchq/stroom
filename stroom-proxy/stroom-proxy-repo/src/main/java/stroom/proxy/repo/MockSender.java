package stroom.proxy.repo;

import stroom.meta.api.AttributeMap;
import stroom.receive.common.StreamHandler;

import java.util.List;

public class MockSender implements Sender {

    @Override
    public void sendDataToHandler(final AttributeMap attributeMap,
                                  final List<SourceItems> items,
                                  final StreamHandler handler) {
    }

    @Override
    public void sendDataToHandler(final RepoSource source, final StreamHandler handler) {
    }
}

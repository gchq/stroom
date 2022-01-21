package stroom.proxy.repo;

import stroom.receive.common.StreamHandler;

import java.util.List;
import java.util.Map;

public class MockSender implements Sender {

    @Override
    public void sendDataToHandler(final Map<RepoSource, List<RepoSourceItem>> items, final StreamHandler handler) {
    }

    @Override
    public void sendDataToHandler(final RepoSource source, final StreamHandler handler) {
    }
}

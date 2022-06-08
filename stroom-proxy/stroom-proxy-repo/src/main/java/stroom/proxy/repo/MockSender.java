package stroom.proxy.repo;

import stroom.receive.common.StreamHandler;

public class MockSender implements Sender {

    @Override
    public void sendDataToHandler(final Items items, final StreamHandler handler) {
    }

    @Override
    public void sendDataToHandler(final RepoSource source, final StreamHandler handler) {
    }
}

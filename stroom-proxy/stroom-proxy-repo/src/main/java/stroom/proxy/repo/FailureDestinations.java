package stroom.proxy.repo;

import stroom.receive.common.StreamHandlers;

public interface FailureDestinations {

    StreamHandlers getProvider(String name);
}

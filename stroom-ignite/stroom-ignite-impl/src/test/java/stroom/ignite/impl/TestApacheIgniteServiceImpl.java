package stroom.ignite.impl;


import stroom.cluster.impl.MockClusterNodeManager;
import stroom.ignite.api.ApacheIgniteService;
import stroom.ignite.mock.MockApacheIgniteModule;
import stroom.ignite.mock.MockLocalhostNodeInfo;

import com.google.inject.Guice;
import com.google.inject.Module;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class TestApacheIgniteServiceImpl {
    private static ApacheIgniteService service;

    @Test
    void testIncrement() {
        System.out.println("The answer is " + service.incrementAndGetCounter(12l,
                "test.ignite.testIncrement", "My Key", Instant.now(), Duration.ofHours(1)));
    }

    @Test
    void testIncrement2() {
        System.out.println("The answer is " + service.incrementAndGetCounter(30l,
                "test.ignite.testIncrement", "My Key", Instant.now(), Duration.ofHours(1)));
    }

    static {
        service = new ApacheIgniteServiceImpl(new MockClusterNodeManager(new MockLocalhostNodeInfo()));
    }

    public static void main(String[] args) {
        System.out.println("The value is now " +
                service.incrementAndGetCounter(9, "test.ignite.main", "TheKey",
                        Instant.now(), Duration.ofHours(1)));
        System.out.println("The value is now " +
                service.incrementAndGetCounter(9, "test.ignite.main", "TheKey",
                        Instant.now(), Duration.ofHours(1)));
        System.out.println("The value is now " +
                service.incrementAndGetCounter(9, "test.ignite.main", "TheKey",
                        Instant.now(), Duration.ofHours(1)));
    }

}

package stroom.hazelcast.impl;

import stroom.cluster.impl.MockClusterModule;
import stroom.hazelcast.api.HazelcastService;
import stroom.hazlecast.mock.MockHazlecastModule;

import com.google.inject.Guice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class TestHazelcastServiceImpl {

    @Inject
    HazelcastService service;

    @BeforeEach
    void setup() {
        List<com.google.inject.Module> modules = new ArrayList<>();
        modules.add(new HazelcastModule());
        modules.add(new MockHazlecastModule());

        Guice.createInjector(modules).injectMembers(this);
    }
    @Test
    void testInitialiseCluster() {

    }

    @Test
    void testIncrement() {
        System.out.println("The answer is " + service.incrementAndGetCounter(12l,
                "test.testHazelcast.testIncrement", "My Key", Instant.now(), Duration.ofHours(1)));
    }

}

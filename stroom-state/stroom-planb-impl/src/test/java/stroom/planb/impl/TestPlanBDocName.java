package stroom.planb.impl;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPlanBDocName {

    @Test
    void testUniqueName() {
        assertThat(PlanBDocStoreImpl.createUniqueName("test",
                Set.of("test"))).isEqualTo("test2");
        assertThat(PlanBDocStoreImpl.createUniqueName("test2",
                Set.of("test"))).isEqualTo("test3");
        assertThat(PlanBDocStoreImpl.createUniqueName("test2",
                Set.of("test", "test2", "test3"))).isEqualTo("test4");
    }
}

package stroom.index.shared;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeState;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;


class TestIndexVolume {

    private static final String TEST_JSON = """
            {
                "id":11,
                "version":1,
                "createTimeMs":1572596376500,
                "createUser":"admin",
                "updateTimeMs":1572596376500,
                "updateUser":"admin",
                "path":"sdfg",
                "nodeName":"node1",
                "state":"ACTIVE",
                "bytesLimit":800,
                "bytesUsed":500,
                "bytesFree":500,
                "bytesTotal":1000,
                "statusMs":1572596376500,
                "indexVolumeGroupId":123
            }""";

    /**
     * Use this method to diagnose why your JSON-to-POJO bindings are failing.
     */
    @Test
    public void testJsonBindings() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        IndexVolume indexVolume = objectMapper.readValue(TEST_JSON, IndexVolume.class);
        Assertions.assertThat(indexVolume)
                .isNotNull();
    }

    @Test
    void testIsFull_belowUserLimit() {
        doIsFullTest(800L, 500L, false);
    }

    @Test
    void testIsFull_aboveUserLimit() {
        doIsFullTest(800L, 850L, true);
    }

    @Test
    void testIsFull_belowHighUserLimit() {
        // User setting a limit == total to effectively prevent limit
        doIsFullTest(1_000L, 500L, false);
    }

    @Test
    void testIsFull_belowSillyUserLimit() {
        // User setting a limit > total so total is limit
        doIsFullTest(1_100L, 500L, false);
    }

    @Test
    void testIsFull_aboveSillyUserLimit() {
        // User setting a limit > total so total is limit
        doIsFullTest(1_100L, 1001L, true);
    }

    @Test
    void testIsFull_belowHardLimit() {
        // Hard coded limit is 99%
        doIsFullTest(null, 850L, false);
    }

    @Test
    void testIsFull_aboveHardLimit() {
        // Hard coded limit is 99%
        doIsFullTest(null, 995L, true);
    }

    private void doIsFullTest(final Long limit,
                              final Long used,
                              final boolean expectedIsFull) {
        final FsVolume fsVolume = new FsVolume();
        fsVolume.setByteLimit(limit);
        final long total = 1000;
        final long free = total - used;
        final FsVolumeState fsVolumeState = new FsVolumeState(
                1,
                1,
                used,
                free,
                total,
                System.currentTimeMillis());
        fsVolume.setVolumeState(fsVolumeState);

        Assertions.assertThat(fsVolume.getCapacityInfo().isFull())
                .isEqualTo(expectedIsFull);
        Assertions.assertThat(fsVolume.getCapacityInfo().getTotalCapacityBytes())
                .hasValue(total);
        Assertions.assertThat(fsVolume.getCapacityInfo().getCapacityUsedBytes())
                .hasValue(used);
        Assertions.assertThat(fsVolume.getCapacityInfo().getFreeCapacityBytes())
                .hasValue(free);
    }
}

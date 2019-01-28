package stroom.index.impl.db;

import java.util.UUID;

public final class TestData {
    private TestData() {

    }

    static String createVolumeGroupName() {
        return String.format("VolumeGroup_%s", UUID.randomUUID());
    }
    static String createVolumeGroupName(final Object o) {
        return String.format("VolumeGroup_%s_%s", o, UUID.randomUUID());
    }
    static String createNodeName() {
        return String.format("Node_%s", UUID.randomUUID());
    }
    static String createNodeName(final Object o) {
        return String.format("Node_%s_%s", o, UUID.randomUUID());
    }
    static String createPath() {
        return String.format("/tmp/index/data/%s", UUID.randomUUID());
    }
    static String createPath(final Object o) {
        return String.format("/tmp/index/data/%s/%s", o, UUID.randomUUID());
    }
}

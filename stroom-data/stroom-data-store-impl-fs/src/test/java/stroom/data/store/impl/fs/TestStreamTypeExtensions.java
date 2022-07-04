package stroom.data.store.impl.fs;

import stroom.data.shared.StreamTypeNames;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStreamTypeExtensions {

    @Test
    void testGetExtension() {
        final StreamTypeExtensions streamTypeExtensions = new StreamTypeExtensions(
                FsVolumeConfig::new);

        Assertions.assertThat(streamTypeExtensions.getExtension(InternalStreamTypeNames.BOUNDARY_INDEX))
                .isEqualTo("bdy");

        Assertions.assertThat(streamTypeExtensions.getExtension(StreamTypeNames.EVENTS))
                .isEqualTo("evt");

        Assertions.assertThat(streamTypeExtensions.getExtension("FOO"))
                .isEqualTo("dat");
    }

    @Test
    void testGetType() {
        final StreamTypeExtensions streamTypeExtensions = new StreamTypeExtensions(
                FsVolumeConfig::new);

        Assertions.assertThat(streamTypeExtensions.getChildType("ctx"))
                .isEqualTo(StreamTypeNames.CONTEXT);

        Assertions.assertThat(streamTypeExtensions.getChildType("mf"))
                .isEqualTo(InternalStreamTypeNames.MANIFEST);

        Assertions.assertThat(streamTypeExtensions.getChildType("meta"))
                .isEqualTo(StreamTypeNames.META);

        Assertions.assertThat(streamTypeExtensions.getChildType("foo"))
                .isNull();
    }
}

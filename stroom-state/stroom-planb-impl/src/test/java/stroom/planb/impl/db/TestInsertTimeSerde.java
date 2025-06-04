package stroom.planb.impl.db;

import stroom.planb.impl.serde.valtime.InsertTimeSerde;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class TestInsertTimeSerde {

    @Test
    void test() {
        final InsertTimeSerde serde = new InsertTimeSerde();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getSize());
        Instant in = DateUtil.parseNormalDateTimeStringToInstant("2025-01-01T00:00:00.000Z");
        serde.write(byteBuffer, in);
        byteBuffer.flip();
        Instant out = serde.read(byteBuffer);
        assertThat(out).isEqualTo(in);

        byteBuffer.clear();

        in = DateUtil.parseNormalDateTimeStringToInstant("2026-01-01T00:00:00.000Z");
        serde.write(byteBuffer, in);
        byteBuffer.flip();
        out = serde.read(byteBuffer);
        assertThat(out).isEqualTo(in);
    }
}

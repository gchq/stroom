package stroom.lmdb;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.time.Instant;


public class TestTemporalKey {

    @Test
    public void testSerDeSer() throws Exception {
        Instant now = Instant.now();
        long nowMs = now.toEpochMilli();

        String key = "MyKey";

        TemporalKey temporalKey1 = new TemporalKey(key, nowMs);

        ByteBuffer dbKey = temporalKey1.toDbKey();

        TemporalKey temporalKey2 = TemporalKey.fromDbKey(dbKey);

        Assert.assertEquals(temporalKey1, temporalKey2);

    }
}
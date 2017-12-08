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

        TemporalKey temporalKey1 = new TemporalKey("MyKey", nowMs);
        serDeser(temporalKey1);

        TemporalKey temporalKey2 = new TemporalKey("", nowMs);
        serDeser(temporalKey1);
    }

    private void serDeser(final TemporalKey inputKey) {

        ByteBuffer dbKey = inputKey.toDbKey();

        TemporalKey temporalKey2 = TemporalKey.fromDbKey(dbKey);

        Assert.assertEquals(inputKey, temporalKey2);
        Assert.assertEquals(inputKey.getKey(), temporalKey2.getKey());
        Assert.assertEquals(inputKey.getTimeMs(), temporalKey2.getTimeMs());

    }
}
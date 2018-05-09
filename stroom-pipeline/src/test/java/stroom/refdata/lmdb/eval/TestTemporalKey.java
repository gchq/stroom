/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package stroom.refdata.lmdb.eval;

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
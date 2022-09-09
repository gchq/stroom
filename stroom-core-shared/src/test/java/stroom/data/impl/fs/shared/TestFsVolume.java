package stroom.data.impl.fs.shared;

import stroom.data.store.impl.fs.shared.FsVolume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestFsVolume {

    private static final String TEST_JSON = """
            {
                "id":11,
                "version":1,
                "createTimeMs":1572596376500,
                "createUser":"admin",
                "updateTimeMs":1572596376500,
                "updateUser":"admin",
                "path":"sdfg",
                "status":"ACTIVE",
                "byteLimit":233887098470,
                "volumeState":{
                    "id":12,
                    "version":6,
                    "bytesUsed":20566679552,
                    "bytesFree":226035576832,
                    "bytesTotal":259874553856,
                    "updateTimeMs":1572597679531,
                    "percentUsed":7
                }
            }""";

    /**
     * Use this method to diagnose why your JSON-to-POJO bindings are failing.
     */
    @Test
    public void testJsonBindings() throws IOException {
        var objectMapper = new ObjectMapper();
        var fsVolume = objectMapper.readValue(TEST_JSON, FsVolume.class);
        Assertions.assertThat(fsVolume)
                .isNotNull();
        Assertions.assertThat(fsVolume.getVolumeState())
                .isNotNull();
    }
}

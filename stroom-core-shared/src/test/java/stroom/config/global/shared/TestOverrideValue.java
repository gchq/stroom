package stroom.config.global.shared;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TestOverrideValue {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOverrideValue.class);

    @Test
    void testSerde() throws IOException {
        final OverrideValue<String> overrideValue = new OverrideValue<>(true, "someValue");

        doSerdeTest(OverrideValue.with("someValue"), OverrideValue.class);
    }

    private <T> void doSerdeTest(final T entity, final Class<T> clazz) throws IOException {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        assertThat(mapper.canSerialize(entity.getClass()))
                .isTrue();

        String json = mapper.writeValueAsString(entity);
        LOGGER.info("\n" + json);

        final T entity2 = (T) mapper.readValue(json, clazz);

        assertThat(entity2).isEqualTo(entity);
    }
}
package stroom.config.global.shared;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestOverrideValue {

    // No LOGGER due to gwt

    @Test
    void testSerde_withValueString() throws IOException {
        doSerdeTest(OverrideValue.with("someValue"), OverrideValue.class);
    }

    @Test
    void testSerde_withValueInt() throws IOException {
        doSerdeTest(OverrideValue.with(123), OverrideValue.class);
    }

    @Test
    void testSerde_withNull() throws IOException {
        doSerdeTest(OverrideValue.with(null), OverrideValue.class);
    }

    @Test
    void testSerde_unset() throws IOException {
        doSerdeTest(OverrideValue.unSet(String.class), OverrideValue.class);
    }

    private <T> void doSerdeTest(final T entity, final Class<T> clazz) throws IOException {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        assertThat(mapper.canSerialize(entity.getClass()))
                .isTrue();

        String json = mapper.writeValueAsString(entity);
        System.out.println("\n" + json);

        final T entity2 = (T) mapper.readValue(json, clazz);

        assertThat(entity2).isEqualTo(entity);
    }
}
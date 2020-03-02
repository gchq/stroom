package stroom.util.shared;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestPropertyPath {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPropertyPath.class);

    @Test
    void getPropertyPath() {
        PropertyPath propertyPath = PropertyPath.fromParts("stroom", "node", "name");
        Assertions.assertThat(propertyPath.toString()).isEqualTo("stroom.node.name");
    }

    @Test
    void merge() {
        PropertyPath propertyPath1 = PropertyPath.fromParts("stroom", "node");
        PropertyPath propertyPath2 = PropertyPath.fromParts("name");
        Assertions.assertThat(propertyPath1.merge(propertyPath2).toString()).isEqualTo("stroom.node.name");
    }

    @Test
    void builder() {
        PropertyPath propertyPath = PropertyPath.builder()
            .add("stroom")
            .add("node")
            .add("name")
            .build();

        Assertions.assertThat(propertyPath.toString()).isEqualTo("stroom.node.name");
    }

    @Test
    void testEqualsIgnoreCase1() {
        doEqualsIgnoreCaseTest("stroom.node.name", "stroom.node.name", true);
    }

    @Test
    void testEqualsIgnoreCase2() {
        doEqualsIgnoreCaseTest("stroom.NODE.name", "stroom.node.name", true);
    }

    @Test
    void testEqualsIgnoreCase3() {
        doEqualsIgnoreCaseTest("stroom.NODE.name", "STROOM.node.NAME", true);
    }

    @Test
    void testEqualsIgnoreCase4() {
        doEqualsIgnoreCaseTest("stroom.node.name", "stroom.xxxxxxx.name", false);
    }

    @Test
    void testEqualsIgnoreCase5() {
        doEqualsIgnoreCaseTest("stroom.node.name", "stroom.node", false);
    }

    private void doEqualsIgnoreCaseTest(final String pathString1, final String pathString2, final boolean expectedResult) {
        PropertyPath path1 = PropertyPath.fromPathString(pathString1);
        PropertyPath path2 = PropertyPath.fromPathString(pathString2);

        boolean result = path1.equalsIgnoreCase(path2);

        Assertions.assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    void testSerde() throws IOException {
        doSerdeTest(PropertyPath.fromPathString("stroom.node.name"), PropertyPath.class);
    }

    @Test
    void testSerde_empty() throws IOException {
        doSerdeTest(PropertyPath.blank(), PropertyPath.class);
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

package stroom.util.shared;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;


class TestPropertyPath {

    @Test
    void getPropertyPath() {
        PropertyPath propertyPath = new PropertyPath("stroom", "node", "name");
        Assertions.assertThat(propertyPath.toString()).isEqualTo("stroom.node.name");
    }

    @Test
    void merge() {
        PropertyPath propertyPath1 = new PropertyPath("stroom", "node");
        PropertyPath propertyPath2 = new PropertyPath("name");
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
}
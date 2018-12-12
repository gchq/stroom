package stroom.feed;


import org.junit.jupiter.api.Test;
import stroom.data.meta.api.AttributeMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMap {
    @Test
    void testSimple() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("person", "person1");

        assertThat(attributeMap.get("person")).isEqualTo("person1");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person1");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Arrays.asList("person")));

        attributeMap.put("PERSON", "person2");

        assertThat(attributeMap.get("person")).isEqualTo("person2");
        assertThat(attributeMap.get("PERSON")).isEqualTo("person2");

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Arrays.asList("PERSON")));

        AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put("persOn", "person3");
        attributeMap2.put("persOn1", "person4");

        attributeMap.putAll(attributeMap2);

        assertThat(attributeMap.keySet()).isEqualTo(new HashSet<>(Arrays.asList("persOn", "persOn1")));
    }

    @Test
    void testRemove() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");

        attributeMap.removeAll(Arrays.asList("A", "b"));

        assertThat(attributeMap.size()).isEqualTo(0);
    }

    @Test
    void testReadWrite() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);
        assertThat(attributeMap.get("a")).isEqualTo("1");
        assertThat(attributeMap.get("b")).isEqualTo("2");
        assertThat(attributeMap.get("z")).isNull();

        assertThat(new String(AttributeMapUtil.toByteArray(attributeMap), AttributeMapUtil.DEFAULT_CHARSET)).isEqualTo("a:1\nb:2\nz\n");
    }

    @Test
    void testtoString() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(AttributeMapUtil.DEFAULT_CHARSET), attributeMap);

        // AttributeMap's are used in log output and so check that they do output
        // the map values.
        assertThat(attributeMap.toString().contains("b=2")).as(attributeMap.toString()).isTrue();
        assertThat(attributeMap.toString().contains("a=1")).as(attributeMap.toString()).isTrue();
    }

    @Test
    void testTrim() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(" person ", "person1");
        attributeMap.put("PERSON", "person2");
        attributeMap.put("FOOBAR", "1");
        attributeMap.put("F OOBAR", "2");
        attributeMap.put(" foobar ", " 3 ");

        assertThat(attributeMap.get("PERSON ")).isEqualTo("person2");
        assertThat(attributeMap.get("FOOBAR")).isEqualTo("3");
    }
}

package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.data.meta.api.AttributeMap;
import stroom.feed.AttributeMapUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class TestAttributeMap {
    @Test
    public void testSimple() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("person", "person1");

        Assert.assertEquals("person1", attributeMap.get("person"));
        Assert.assertEquals("person1", attributeMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("person")), attributeMap.keySet());

        attributeMap.put("PERSON", "person2");

        Assert.assertEquals("person2", attributeMap.get("person"));
        Assert.assertEquals("person2", attributeMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("PERSON")), attributeMap.keySet());

        AttributeMap attributeMap2 = new AttributeMap();
        attributeMap2.put("persOn", "person3");
        attributeMap2.put("persOn1", "person4");

        attributeMap.putAll(attributeMap2);

        Assert.assertEquals(new HashSet<>(Arrays.asList("persOn", "persOn1")), attributeMap.keySet());

    }

    @Test
    public void testRemove() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("a", "a1");
        attributeMap.put("B", "b1");

        attributeMap.removeAll(Arrays.asList("A", "b"));

        Assert.assertEquals(0, attributeMap.size());
    }

    @Test
    public void testReadWrite() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(CharsetConstants.DEFAULT_CHARSET), attributeMap);
        Assert.assertEquals("1", attributeMap.get("a"));
        Assert.assertEquals("2", attributeMap.get("b"));
        Assert.assertNull(attributeMap.get("z"));

        Assert.assertEquals("a:1\nb:2\nz\n", new String(AttributeMapUtil.toByteArray(attributeMap), CharsetConstants.DEFAULT_CHARSET));
    }

    @Test
    public void testtoString() throws IOException {
        AttributeMap attributeMap = new AttributeMap();
        AttributeMapUtil.read("b:2\na:1\nz\n".getBytes(CharsetConstants.DEFAULT_CHARSET), attributeMap);

        // AttributeMap's are used in log output and so check that they do output
        // the map values.
        Assert.assertTrue(attributeMap.toString(), attributeMap.toString().contains("b=2"));
        Assert.assertTrue(attributeMap.toString(), attributeMap.toString().contains("a=1"));
    }

    @Test
    public void testTrim() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(" person ", "person1");
        attributeMap.put("PERSON", "person2");
        attributeMap.put("FOOBAR", "1");
        attributeMap.put("F OOBAR", "2");
        attributeMap.put(" foobar ", " 3 ");

        Assert.assertEquals("person2", attributeMap.get("PERSON "));
        Assert.assertEquals("3", attributeMap.get("FOOBAR"));
    }
}

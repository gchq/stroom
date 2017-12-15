package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.MetaMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class TestMetaMap {
    @Test
    public void testSimple() {
        MetaMap metaMap = new MetaMap();
        metaMap.put("person", "person1");

        Assert.assertEquals("person1", metaMap.get("person"));
        Assert.assertEquals("person1", metaMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("person")), metaMap.keySet());

        metaMap.put("PERSON", "person2");

        Assert.assertEquals("person2", metaMap.get("person"));
        Assert.assertEquals("person2", metaMap.get("PERSON"));

        Assert.assertEquals(new HashSet<>(Arrays.asList("PERSON")), metaMap.keySet());

        MetaMap metaMap2 = new MetaMap();
        metaMap2.put("persOn", "person3");
        metaMap2.put("persOn1", "person4");

        metaMap.putAll(metaMap2);

        Assert.assertEquals(new HashSet<>(Arrays.asList("persOn", "persOn1")), metaMap.keySet());

    }

    @Test
    public void testRemove() {
        MetaMap metaMap = new MetaMap();
        metaMap.put("a", "a1");
        metaMap.put("B", "b1");

        metaMap.removeAll(Arrays.asList("A", "b"));

        Assert.assertEquals(0, metaMap.size());
    }

    @Test
    public void testReadWrite() throws IOException {
        MetaMap metaMap = new MetaMap();
        metaMap.read("b:2\na:1\nz\n".getBytes(CharsetConstants.DEFAULT_CHARSET));
        Assert.assertEquals("1", metaMap.get("a"));
        Assert.assertEquals("2", metaMap.get("b"));
        Assert.assertNull(metaMap.get("z"));

        Assert.assertEquals("a:1\nb:2\nz\n", new String(metaMap.toByteArray(), CharsetConstants.DEFAULT_CHARSET));
    }

    @Test
    public void testtoString() throws IOException {
        MetaMap metaMap = new MetaMap();
        metaMap.read("b:2\na:1\nz\n".getBytes(CharsetConstants.DEFAULT_CHARSET));

        // MetaMap's are used in log output and so check that they do output
        // the map values.
        Assert.assertTrue(metaMap.toString(), metaMap.toString().contains("b=2"));
        Assert.assertTrue(metaMap.toString(), metaMap.toString().contains("a=1"));
    }

    @Test
    public void testTrim() {
        MetaMap metaMap = new MetaMap();
        metaMap.put(" person ", "person1");
        metaMap.put("PERSON", "person2");
        metaMap.put("FOOBAR", "1");
        metaMap.put("F OOBAR", "2");
        metaMap.put(" foobar ", " 3 ");

        Assert.assertEquals("person2", metaMap.get("PERSON "));
        Assert.assertEquals("3", metaMap.get("FOOBAR"));
    }
}

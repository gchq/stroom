package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Test;
import stroom.feed.AttributeMap;

public class TestStroomFileNameUtil {
    @Test
    public void testPad() {
        Assert.assertEquals("001", StroomFileNameUtil.getIdPath(1));
        Assert.assertEquals("999", StroomFileNameUtil.getIdPath(999));
        Assert.assertEquals("001/001000", StroomFileNameUtil.getIdPath(1000));
        Assert.assertEquals("001/001999", StroomFileNameUtil.getIdPath(1999));
        Assert.assertEquals("009/111/009111999", StroomFileNameUtil.getIdPath(9111999));
    }

    @Test
    public void testConstructFilename() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("var1", "myVar1");

        final String standardTemplate = "${pathId}/${id}";
        final String staticTemplate = "${pathId}/${id} someStaticText";
        final String dynamicTemplate = "${id} ${var1} ${feed}";

        final String extension1 = ".zip";
        final String extension2 = ".bad";

        Assert.assertEquals("001.zip.bad", StroomFileNameUtil.constructFilename(1, standardTemplate, attributeMap, extension1, extension2));
        Assert.assertEquals("003/003000.zip", StroomFileNameUtil.constructFilename(3000, standardTemplate, attributeMap, extension1));
        Assert.assertEquals("003000_myVar1_myFeed.zip", StroomFileNameUtil.constructFilename(3000, dynamicTemplate, attributeMap, extension1));
        Assert.assertEquals("003/003000_someStaticText.zip", StroomFileNameUtil.constructFilename(3000, staticTemplate, attributeMap, extension1));
        Assert.assertEquals("003/003000_someStaticText", StroomFileNameUtil.constructFilename(3000, staticTemplate, attributeMap));
    }
}
package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;

public class TestPathCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestPathCreator.class);

    private final MetaMap metaMap = new MetaMap();

    @Before
    public void setup() {
        metaMap.put("feed", "myFeed");
        metaMap.put("type1", "mytype1");
        metaMap.put("type2", "mytype2");
    }

    @Test
    public void testFindVars() {
        final String[] vars = PathCreator.findVars("/temp/${feed}-FLAT/${pipe}_less-${uuid}/${searchId}");
        Assert.assertEquals(4, vars.length);
        Assert.assertEquals("feed", vars[0]);
        Assert.assertEquals("pipe", vars[1]);
        Assert.assertEquals("uuid", vars[2]);
        Assert.assertEquals("searchId", vars[3]);
    }

    @Test
    public void testReplace() {
        final String template = "someText_${type1}_someText_${feed}_someText_${type2}_someText";

        final String result = PathCreator.replaceAll(template, metaMap);

        LOGGER.info("result: %s", result);
        Assert.assertEquals("someText_mytype1_someText_myFeed_someText_mytype2_someText", result);
    }
}
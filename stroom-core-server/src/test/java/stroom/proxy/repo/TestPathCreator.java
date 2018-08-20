package stroom.proxy.repo;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.feed.MetaMap;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

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

    @Test
    public void testReplaceTime() {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 8, 20, 13, 17, 22, 2111444, ZoneOffset.UTC);
        final MetaMap metaMap = new MetaMap();
        metaMap.put("feed", "TEST");

        String path = "${feed}/${year}/${year}-${month}/${year}-${month}-${day}/${pathId}/${id}";

        // Replace pathId variable with path id.
        path = PathCreator.replace(path, "pathId", "1234");
        // Replace id variable with file id.
        path = PathCreator.replace(path, "id", "5678");

        Assert.assertEquals("${feed}/${year}/${year}-${month}/${year}-${month}-${day}/1234/5678", path);

        path = PathCreator.replaceTimeVars(path, zonedDateTime);

        Assert.assertEquals("${feed}/2018/2018-08/2018-08-20/1234/5678", path);

        path = PathCreator.replaceAll(path, metaMap);

        Assert.assertEquals("TEST/2018/2018-08/2018-08-20/1234/5678", path);
    }
}
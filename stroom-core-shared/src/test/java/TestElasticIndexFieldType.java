import stroom.search.elastic.shared.ElasticIndexConstants;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.test.StroomJUnit4ClassRunner;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestElasticIndexFieldType {
    @Test
    public void testFromNativeType() {
        // ID fields
        Assert.assertSame(ElasticIndexFieldType.ID, ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.EVENT_ID, "long"));
        Assert.assertNotSame("Non-ID field is not detected as an ID field",
            ElasticIndexFieldType.INTEGER, ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.FEED_ID, "int")
        );

        Assert.assertSame(ElasticIndexFieldType.BOOLEAN, ElasticIndexFieldType.fromNativeType("name", "boolean"));
        Assert.assertSame(ElasticIndexFieldType.INTEGER, ElasticIndexFieldType.fromNativeType("name", "integer"));
        Assert.assertSame(ElasticIndexFieldType.LONG, ElasticIndexFieldType.fromNativeType("name", "long"));
        Assert.assertSame(ElasticIndexFieldType.FLOAT, ElasticIndexFieldType.fromNativeType("name", "float"));
        Assert.assertSame(ElasticIndexFieldType.DOUBLE, ElasticIndexFieldType.fromNativeType("name", "double"));
        Assert.assertSame(ElasticIndexFieldType.DATE, ElasticIndexFieldType.fromNativeType("name", "date"));
        Assert.assertSame(ElasticIndexFieldType.TEXT, ElasticIndexFieldType.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() {
        Assert.assertTrue(ElasticIndexFieldType.INTEGER.isNumeric());
        Assert.assertTrue(ElasticIndexFieldType.LONG.isNumeric());
        Assert.assertFalse(ElasticIndexFieldType.FLOAT.isNumeric());
        Assert.assertFalse(ElasticIndexFieldType.DOUBLE.isNumeric());
    }
}

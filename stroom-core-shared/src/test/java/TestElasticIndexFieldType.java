import stroom.search.elastic.shared.ElasticIndexConstants;
import stroom.search.elastic.shared.ElasticIndexFieldType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestElasticIndexFieldType {
    @Test
    public void testFromNativeType() {
        // ID fields
        Assertions.assertSame(ElasticIndexFieldType.ID, ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.EVENT_ID, "long"));
        Assertions.assertNotSame(ElasticIndexFieldType.INTEGER, ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.FEED_ID, "int"), "Non-ID field is not detected as an ID field");

        Assertions.assertSame(ElasticIndexFieldType.BOOLEAN, ElasticIndexFieldType.fromNativeType("name", "boolean"));
        Assertions.assertSame(ElasticIndexFieldType.INTEGER, ElasticIndexFieldType.fromNativeType("name", "integer"));
        Assertions.assertSame(ElasticIndexFieldType.LONG, ElasticIndexFieldType.fromNativeType("name", "long"));
        Assertions.assertSame(ElasticIndexFieldType.FLOAT, ElasticIndexFieldType.fromNativeType("name", "float"));
        Assertions.assertSame(ElasticIndexFieldType.DOUBLE, ElasticIndexFieldType.fromNativeType("name", "double"));
        Assertions.assertSame(ElasticIndexFieldType.DATE, ElasticIndexFieldType.fromNativeType("name", "date"));
        Assertions.assertSame(ElasticIndexFieldType.TEXT, ElasticIndexFieldType.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() {
        Assertions.assertTrue(ElasticIndexFieldType.INTEGER.isNumeric());
        Assertions.assertTrue(ElasticIndexFieldType.LONG.isNumeric());
        Assertions.assertTrue(ElasticIndexFieldType.FLOAT.isNumeric());
        Assertions.assertTrue(ElasticIndexFieldType.DOUBLE.isNumeric());
    }
}

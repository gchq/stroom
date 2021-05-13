import stroom.search.elastic.shared.ElasticIndexConstants;
import stroom.search.elastic.shared.ElasticIndexFieldType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() {
        // ID fields
        assertSame(ElasticIndexFieldType.ID,
                ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.EVENT_ID, "long"));
        assertNotSame(ElasticIndexFieldType.INTEGER,
                ElasticIndexFieldType.fromNativeType(ElasticIndexConstants.FEED_ID, "int"),
                "Non-ID field is not detected as an ID field");

        assertSame(ElasticIndexFieldType.BOOLEAN,
                ElasticIndexFieldType.fromNativeType("name", "boolean"));
        assertSame(ElasticIndexFieldType.INTEGER,
                ElasticIndexFieldType.fromNativeType("name", "integer"));
        assertSame(ElasticIndexFieldType.LONG,
                ElasticIndexFieldType.fromNativeType("name", "long"));
        assertSame(ElasticIndexFieldType.FLOAT,
                ElasticIndexFieldType.fromNativeType("name", "float"));
        assertSame(ElasticIndexFieldType.DOUBLE,
                ElasticIndexFieldType.fromNativeType("name", "double"));
        assertSame(ElasticIndexFieldType.DATE,
                ElasticIndexFieldType.fromNativeType("name", "date"));
        assertSame(ElasticIndexFieldType.TEXT,
                ElasticIndexFieldType.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() {
        assertTrue(ElasticIndexFieldType.INTEGER.isNumeric());
        assertTrue(ElasticIndexFieldType.LONG.isNumeric());
        assertTrue(ElasticIndexFieldType.FLOAT.isNumeric());
        assertTrue(ElasticIndexFieldType.DOUBLE.isNumeric());
    }
}

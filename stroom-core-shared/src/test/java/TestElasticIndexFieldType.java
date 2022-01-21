import stroom.search.elastic.shared.ElasticIndexFieldType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() {
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
        assertFalse(ElasticIndexFieldType.FLOAT.isNumeric());
        assertFalse(ElasticIndexFieldType.DOUBLE.isNumeric());
    }
}

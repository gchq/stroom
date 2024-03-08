import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.search.elastic.shared.ElasticNativeTypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() {
        assertSame(ElasticIndexFieldType.BOOLEAN,
                ElasticNativeTypes.fromNativeType("name", "boolean"));
        assertSame(ElasticIndexFieldType.INTEGER,
                ElasticNativeTypes.fromNativeType("name", "integer"));
        assertSame(ElasticIndexFieldType.LONG,
                ElasticNativeTypes.fromNativeType("name", "long"));
        assertSame(ElasticIndexFieldType.FLOAT,
                ElasticNativeTypes.fromNativeType("name", "float"));
        assertSame(ElasticIndexFieldType.DOUBLE,
                ElasticNativeTypes.fromNativeType("name", "double"));
        assertSame(ElasticIndexFieldType.DATE,
                ElasticNativeTypes.fromNativeType("name", "date"));
        assertSame(ElasticIndexFieldType.TEXT,
                ElasticNativeTypes.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() {
        assertTrue(ElasticIndexFieldType.INTEGER.isNumeric());
        assertTrue(ElasticIndexFieldType.LONG.isNumeric());
        assertFalse(ElasticIndexFieldType.FLOAT.isNumeric());
        assertFalse(ElasticIndexFieldType.DOUBLE.isNumeric());
    }
}

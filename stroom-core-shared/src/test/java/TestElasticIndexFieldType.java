import stroom.datasource.api.v2.FieldType;
import stroom.search.elastic.shared.ElasticNativeTypes;
import stroom.search.elastic.shared.UnsupportedTypeException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestElasticIndexFieldType {

    @Test
    public void testFromNativeType() throws UnsupportedTypeException {
        assertSame(FieldType.BOOLEAN,
                ElasticNativeTypes.fromNativeType("name", "boolean"));
        assertSame(FieldType.INTEGER,
                ElasticNativeTypes.fromNativeType("name", "integer"));
        assertSame(FieldType.LONG,
                ElasticNativeTypes.fromNativeType("name", "long"));
        assertSame(FieldType.FLOAT,
                ElasticNativeTypes.fromNativeType("name", "float"));
        assertSame(FieldType.DOUBLE,
                ElasticNativeTypes.fromNativeType("name", "double"));
        assertSame(FieldType.DATE,
                ElasticNativeTypes.fromNativeType("name", "date"));
        assertSame(FieldType.TEXT,
                ElasticNativeTypes.fromNativeType("name", "text"));
    }

    @Test
    public void testIsNumeric() throws UnsupportedTypeException {
        assertTrue(FieldType.INTEGER.isNumeric());
        assertTrue(FieldType.LONG.isNumeric());
        assertFalse(FieldType.FLOAT.isNumeric());
        assertFalse(FieldType.DOUBLE.isNumeric());
    }
}

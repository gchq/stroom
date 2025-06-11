import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticNativeTypes;
import stroom.search.elastic.shared.UnsupportedTypeException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

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
        assertTrue(FieldType.FLOAT.isNumeric());
        assertTrue(FieldType.DOUBLE.isNumeric());
    }

    @Test
    void testNotFound() {
        Assertions.assertThatThrownBy(() -> {
                    ElasticNativeTypes.fromNativeType("name", "foo");
                })
                .isInstanceOf(UnsupportedTypeException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("foo");
    }
}

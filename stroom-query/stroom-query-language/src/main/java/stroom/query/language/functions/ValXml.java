package stroom.query.language.functions;

import stroom.util.xml.XMLUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.xml.sax.InputSource;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ValXml implements Val {

    private static final Comparator<Val> CASE_SENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValString.class,
            ValComparators.AS_DOUBLE_THEN_CASE_SENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_SENSITIVE_COMPARATOR);
    private static final Comparator<Val> CASE_INSENSITIVE_COMPARATOR = ValComparators.asGenericComparator(
            ValString.class,
            ValComparators.AS_DOUBLE_THEN_CASE_INSENSITIVE_STRING_COMPARATOR,
            ValComparators.GENERIC_CASE_INSENSITIVE_COMPARATOR);

    public static final Type TYPE = Type.XML;
    @JsonProperty
    private final byte[] bytes;
    @JsonIgnore
    private String stringValue;

    @JsonCreator
    private ValXml(@JsonProperty("bytes") final byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public static ValXml create(final byte[] bytes) {
        return new ValXml(bytes);
    }

    @Override
    public Integer toInteger() {
        return null;
    }

    @Override
    public Long toLong() {
        return null;
    }

    @Override
    public Float toFloat() {
        return null;
    }

    @Override
    public Double toDouble() {
        return null;
    }

    @Override
    public Boolean toBoolean() {
        return null;
    }

    @Override
    public Object unwrap() {
        return null;
    }

    @Override
    public Type type() {
        return TYPE;
    }

    @Override
    public Comparator<Val> getDefaultComparator(final boolean isCaseSensitive) {
        return isCaseSensitive
                ? CASE_SENSITIVE_COMPARATOR
                : CASE_INSENSITIVE_COMPARATOR;
    }

    @Override
    public String toString() {
        if (stringValue == null) {
            try {
                stringValue = byteBufferToString(bytes);
            } catch (final RuntimeException e) {
                stringValue = new String(bytes, StandardCharsets.UTF_8);
            }
        }
        return stringValue;
    }

    private static String byteBufferToString(final byte[] bytes) {
        try {
            final Writer writer = new StringWriter(1000);
            final SAXDocumentParser parser = new SAXDocumentParser();
            XMLUtil.prettyPrintXML(parser, new InputSource(new ByteBufferInputStream(ByteBuffer.wrap(bytes))), writer);
            return writer.toString();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void appendString(final StringBuilder sb) {
        final String val = toString();
        // Assume that strings are single quoted even though they may actually be double quoted in source.
        sb.append("'");
        sb.append(val);
        sb.append("'");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ValXml valXml = (ValXml) o;
        return Arrays.equals(bytes, valXml.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}

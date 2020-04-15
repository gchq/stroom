package stroom.pipeline.reader;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.xml.XMLUtil;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class TestInvalidCharFilterReader {
    @Test
    public void test() throws IOException {
        char[] good = {'v', 'a', 'l', 'u', 'e'};
        char[] bad = {5, 'v', 'a', 'l', 'u', 'e'};

        final Params goodParms = new Params(new String(good), new String(good));
        final Params badParms = new Params(new String(bad), new String(bad));

        final String goodXml = appendRecords(new StringBuilder(), goodParms).toString();
        final String badXml = appendRecords(new StringBuilder(), badParms).toString();

        Assert.assertNotEquals(goodXml, badXml);

        final String convertGoodXml = XMLUtil.prettyPrintXML(goodXml);
        final String convertBadXml = convert(badXml);

        Assert.assertEquals(convertGoodXml, convertBadXml);
    }

    @Test
    public void test2() throws IOException {
        char[] good = {'v', 'a', 'l', 'u', 'e'};
        char[] bad = {5, 'v', 'a', 'l', 'u', 'e'};

        final Params goodParms = new Params(new String(good), new String(good));
        final Params badParms = new Params(new String(bad), new String(bad));

        final String goodXml = appendRecords(new StringBuilder(), goodParms).toString();
        final String badXml = appendRecords(new StringBuilder(), badParms).toString();

        Assert.assertNotEquals(goodXml, badXml);

        final String convertGoodXml = XMLUtil.prettyPrintXML(goodXml);
        final String convertBadXml = convert2(badXml);

        Assert.assertEquals(convertGoodXml, convertBadXml);
    }

    private String convert(final String string) throws IOException {
        final StringWriter writer = new StringWriter();
        try (final InvalidXmlCharFilter reader = new InvalidXmlCharFilter(new StringReader(string), new Xml10Chars())) {
            XMLUtil.prettyPrintXML(reader, writer);
        }
        return writer.toString();
    }

    private String convert2(final String string) throws IOException {
        final StringWriter writer = new StringWriter();
        try (final FindReplaceFilter reader = new FindReplaceFilter.Builder()
                .find("\u0005")
                .replacement("")
                .reader(new StringReader(string))
                .build()) {
            XMLUtil.prettyPrintXML(reader, writer);
        }
        return writer.toString();
    }


//        try (final InvalidCharFilterReader reader = new InvalidCharFilterReader(new StringReader(string))) {
//
//            final List<CharSlice> slices = new ArrayList<>();
//            try {
//                int len = 0;
//                while (len != -1) {
//                    final char[] buffer = new char[1000];
//                    len = reader.read(buffer, 0, buffer.length);
//                    if (len != -1) {
//                        slices.add(new CharSlice(buffer, 0, len));
//                    }
//                }
//
//                final StringBuilder sb = new StringBuilder();
//                slices.forEach(slice -> {
//                    sb.append(slice.toString());
//                });
//
//                return sb.toString();
//            } catch (final IOException ioEx) {
//                // Wrap it
//                throw new RuntimeException(ioEx);
//            }
//        }

    private StringBuilder appendRecords(final StringBuilder sb, final Params params) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<records>\n");
        for (int i = 0; i < 1000; i++) {
            appendRecord(sb, params);
        }
        sb.append("</records>\n");
        return sb;
    }

    private StringBuilder appendRecord(final StringBuilder sb, final Params params) {
        sb.append("  <record>\n");
        for (int i = 0; i < 10; i++) {
            appendData(sb, params);
        }
        sb.append("  </record>\n");
        return sb;
    }

    private StringBuilder appendData(final StringBuilder sb, final Params params) {
        sb.append("    <data name=\"");
        sb.append(params.attributeValue);
        sb.append("\">\n");
        sb.append(params.content);
        sb.append("\n    </data>\n");
        return sb;
    }

    private static class Params {
        private final String attributeValue;
        private final String content;

        public Params(final String attributeValue, final String content) {
            this.attributeValue = attributeValue;
            this.content = content;
        }
    }
}

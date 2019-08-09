package stroom.pipeline;

import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import stroom.util.xml.FatalErrorListener;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.util.xml.XMLUtil;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

class TestXmlWriter {
    @Test
    void testUnicodeHandling() throws Exception {
        final StringBuilder sb = new StringBuilder();
        for (int i = '\u0001'; i <= '\uffff'; i++) {
            char c = (char) i;
            if (!UTF16CharacterSet.isSurrogate(c)) {
                sb.append(c);
            }
        }

        final String text = sb.toString();
        final char[] characters = text.toCharArray();
        final AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "att", "att", "string", text);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final TransformerHandler th = XMLUtil.createTransformerHandler(new FatalErrorListener(), true);
        th.setResult(new StreamResult(baos));

        th.startDocument();
        th.startElement("", "test", "test", attributes);
        th.characters(characters, 0, characters.length);
        th.endElement("", "test", "test");
        th.endDocument();

        final byte[] bytes = baos.toByteArray();
        final String string = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);

        final XMLReader xmlReader = SAXParserFactoryFactory.newInstance().newSAXParser().getXMLReader();
        xmlReader.parse(new InputSource(new StringReader(string)));
    }
}

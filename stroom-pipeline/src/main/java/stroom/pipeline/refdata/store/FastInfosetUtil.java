package stroom.pipeline.refdata.store;

import stroom.util.xml.XMLUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.xml.sax.InputSource;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;

public class FastInfosetUtil {

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        try {
            final Writer writer = new StringWriter(1000);
            final SAXDocumentParser parser = new SAXDocumentParser();
            XMLUtil.prettyPrintXML(parser, new InputSource(new ByteBufferInputStream(byteBuffer.duplicate())), writer);
            return writer.toString();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}

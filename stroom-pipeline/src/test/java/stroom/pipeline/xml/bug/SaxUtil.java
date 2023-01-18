package stroom.pipeline.xml.bug;

import stroom.util.xml.SAXParserFactoryFactory;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;

public class SaxUtil {

    public static void parse(final Reader input,
                             final ContentHandler contentHandler,
                             final EntityResolver resolver) throws Exception {
        final SAXParser parser = SAXParserFactoryFactory.newInstance().newSAXParser();

        final XMLReader xmlReader = parser.getXMLReader();
        xmlReader.setEntityResolver(resolver);
        xmlReader.setContentHandler(contentHandler);
//        xmlReader.setErrorHandler(getErrorHandler());
        xmlReader.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);

        final InputSource inputSource = new InputSource(input);
        inputSource.setEncoding("UTF-8");
        xmlReader.parse(inputSource);
    }
}

package stroom.pipeline.xml.bug;

import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class SaxUtil {

    public static SAXParserFactory newParserFactory() {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        secureProcessing(factory);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        return factory;
    }

    private static void secureProcessing(final SAXParserFactory factory) {
        try {
            factory.setFeature(
                    XMLConstants.FEATURE_SECURE_PROCESSING,
                    false);

        } catch (final ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void parse(final Reader input,
                             final ContentHandler contentHandler,
                             final EntityResolver resolver) throws Exception {
        final SAXParser parser = newParserFactory().newSAXParser();

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

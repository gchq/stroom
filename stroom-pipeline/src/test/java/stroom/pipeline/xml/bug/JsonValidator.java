package stroom.pipeline.xml.bug;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonParser.Feature;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.IOException;

public class JsonValidator implements ContentHandler {

    private static final String ROW = "row";

    private final JsonFactory jsonFactory;
    private int rowCount;
    private final StringBuilder sb = new StringBuilder();
    private boolean inRow;
    private int errorCount;

    public JsonValidator() {
        jsonFactory = new JsonFactory();
        jsonFactory.configure(Feature.ALLOW_COMMENTS, false);
        jsonFactory.configure(Feature.ALLOW_YAML_COMMENTS, false);
        jsonFactory.configure(Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        jsonFactory.configure(Feature.ALLOW_SINGLE_QUOTES, true);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) {
        if (localName.equalsIgnoreCase(ROW)) {
            inRow = true;
        }
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        if (localName.equalsIgnoreCase(ROW)) {
            try {
                final JsonParser jp = jsonFactory.createParser(sb.toString());

                while (jp.nextToken() != null) {
                }
            } catch (final IOException e) {
                errorCount++;
                System.err.println("ERROR PARSING JSON EVENT " + rowCount + " : " + e.getMessage());
            }

            sb.setLength(0);
            inRow = false;
            rowCount++;
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
        if (inRow) {
            sb.append(ch, start, length);
        }
    }

    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public void setDocumentLocator(Locator locator) {

    }

    @Override
    public void startDocument() throws SAXException {

    }

    @Override
    public void endDocument() throws SAXException {

    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {

    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {

    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {

    }

    @Override
    public void skippedEntity(String name) throws SAXException {

    }
}

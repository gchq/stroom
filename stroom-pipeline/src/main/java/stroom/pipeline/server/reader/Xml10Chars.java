package stroom.pipeline.server.reader;

import com.sun.org.apache.xml.internal.utils.XMLChar;

public class Xml10Chars implements XmlChars {
    @Override
    public boolean isValid(final int c) {
        return XMLChar.isValid(c);
    }
}

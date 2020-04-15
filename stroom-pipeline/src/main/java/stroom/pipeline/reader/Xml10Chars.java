package stroom.pipeline.reader;

import stroom.util.xml.XMLChar;

public class Xml10Chars implements XmlChars {
    @Override
    public boolean isValid(final int c) {
        return XMLChar.isValid(c);
    }
}

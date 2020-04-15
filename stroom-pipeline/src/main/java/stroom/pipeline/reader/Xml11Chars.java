package stroom.pipeline.reader;

import stroom.util.xml.XML11Char;

public class Xml11Chars implements XmlChars {
    @Override
    public boolean isValid(final int c) {
        return XML11Char.isXML11Valid(c);
    }
}

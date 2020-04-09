package stroom.pipeline.server.reader;

import com.sun.org.apache.xml.internal.utils.XML11Char;

public class Xml11Chars implements XmlChars {
    @Override
    public boolean isValid(final int c) {
        return XML11Char.isXML11Valid(c);
    }
}

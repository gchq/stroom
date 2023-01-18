package stroom.pipeline.xml.bug;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class NullEntityResolver implements EntityResolver {

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        System.out.println(publicId);
        System.out.println(systemId);
        return null;
    }
}

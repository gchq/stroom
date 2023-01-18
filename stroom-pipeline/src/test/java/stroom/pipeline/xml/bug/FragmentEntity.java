package stroom.pipeline.xml.bug;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class FragmentEntity implements EntityResolver {

    private static final String FRAGMENT = "fragment";
    private final InputSource fragment;

    FragmentEntity(final InputSource fragment) {
        this.fragment = fragment;
    }

    @Override
    public InputSource resolveEntity(final String publicId, final String systemId) {
        if ((publicId != null && publicId.endsWith(FRAGMENT))
                || (systemId != null && systemId.endsWith(FRAGMENT))) {
            return fragment;
        }

        return null;
    }
}

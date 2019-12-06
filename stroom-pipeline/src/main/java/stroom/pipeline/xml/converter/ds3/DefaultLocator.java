package stroom.pipeline.xml.converter.ds3;

import org.xml.sax.Locator;

public interface DefaultLocator extends Locator {
    default String getPublicId() {
        return null;
    }

    default String getSystemId() {
        return null;
    }
}
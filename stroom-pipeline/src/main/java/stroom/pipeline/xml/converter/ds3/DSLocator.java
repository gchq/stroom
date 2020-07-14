package stroom.pipeline.xml.converter.ds3;

import org.xml.sax.Locator;

public interface DSLocator extends DefaultLocator {
    Locator getRecordStartLocator();
    Locator getRecordEndLocator();
}

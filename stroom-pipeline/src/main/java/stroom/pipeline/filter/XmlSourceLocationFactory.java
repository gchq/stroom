package stroom.pipeline.filter;

import org.xml.sax.Locator;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Highlight;
import stroom.util.shared.Location;

import java.util.ArrayList;
import java.util.List;

class XmlSourceLocationFactory implements SourceLocationFactory {
    private final MetaHolder metaHolder;
    private final LocationHolder locationHolder;
    private final Locator locator;
    private final int maxSize;
    private long recordNo;
    private Location currentLocation = new DefaultLocation(1, 0);
    private boolean storeLocations = true;

    XmlSourceLocationFactory(final MetaHolder metaHolder, final LocationHolder locationHolder, final Locator locator, final int maxSize) {
        this.metaHolder = metaHolder;
        this.locationHolder = locationHolder;
        this.locator = locator;
        this.maxSize = maxSize;
    }

    @Override
    public void reset() {
        recordNo = 0;
        currentLocation = new DefaultLocation(1, 0);
    }

    @Override
    public void storeLocation() {
        if (storeLocations && locationHolder != null && metaHolder != null && metaHolder.getMeta() != null) {
            recordNo++;

            final Location location = new DefaultLocation(locator.getLineNumber(), locator.getColumnNumber());
            final Highlight highlight = new Highlight();
            highlight.setFrom(currentLocation);
            highlight.setTo(location);
            currentLocation = location;

            final SourceLocation sourceLocation = new SourceLocation(metaHolder.getMeta().getId(), metaHolder.getChildDataType(), metaHolder.getStreamNo(), recordNo, highlight);
            if (maxSize <= 1) {
                locationHolder.setCurrentLocation(sourceLocation);

            } else {
                List<SourceLocation> locations = locationHolder.getLocations();
                if (locations == null) {
                    locations = new ArrayList<>();
                    locationHolder.setLocations(locations);
                }
                locations.add(sourceLocation);

                // If locations aren't being consumed then stop recording them.
                if (locations.size() > maxSize + 10) {
                    storeLocations = false;
                    locations.clear();
                }
            }
        }
    }
}

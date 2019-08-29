package stroom.pipeline.server.filter;

import org.xml.sax.Locator;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Highlight;
import stroom.util.shared.Location;

import java.util.ArrayList;
import java.util.List;

class XmlSourceLocationFactory implements SourceLocationFactory {
    private final StreamHolder streamHolder;
    private final LocationHolder locationHolder;
    private final Locator locator;
    private final int maxSize;
    private long recordNo;
    private Location currentLocation = new DefaultLocation(1, 0);
    private boolean storeLocations = true;

    XmlSourceLocationFactory(final StreamHolder streamHolder, final LocationHolder locationHolder, final Locator locator, final int maxSize) {
        this.streamHolder = streamHolder;
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
        if (storeLocations && locationHolder != null && streamHolder != null && streamHolder.getStream() != null) {
            recordNo++;

            final Location location = new DefaultLocation(locator.getLineNumber(), locator.getColumnNumber());
            final Highlight highlight = new Highlight();
            highlight.setFrom(currentLocation);
            highlight.setTo(location);
            currentLocation = location;

            final SourceLocation sourceLocation = new SourceLocation(streamHolder.getStream().getId(), streamHolder.getChildStreamType(), streamHolder.getStreamNo(), recordNo, highlight);
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

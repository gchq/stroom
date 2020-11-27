/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.state;

import stroom.util.shared.DataRange;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.xml.converter.ds3.DSLocator;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.TextRange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Locator;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@PipelineScoped
public class LocationHolder implements Holder {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocationHolder.class);
    private final MetaHolder metaHolder;
    private List<SourceLocation> locations;
    private SourceLocation currentLocation;
    private FunctionType functionType;

    private Locator locator;
    private int maxSize = 1;
    private boolean storeLocations = true;
    private long recordNo;
    private Location currentStartLocation;
    private Location currentEndLocation;
    private Location markedStartLocation;
    private boolean hasMarkedStartLocation;
    private static final Comparator<Location> LOCATION_COMPARATOR = Comparator
            .comparing(Location::getLineNo)
            .thenComparing(Location::getColNo);

    @Inject
    public LocationHolder(final MetaHolder metaHolder) {
        this.metaHolder = metaHolder;
        reset();
    }

    public void setDocumentLocator(final Locator locator, final int maxSize) {
        this.maxSize = maxSize;
        this.locator = locator;
    }

    public void move(final FunctionType functionType) {
        if (this.functionType == null) {
            this.functionType = functionType;
        }

        if (this.functionType == functionType && locations != null) {
            if (locations.size() > 0) {
                currentLocation = locations.remove(0);
            } else {
                currentLocation = null;
            }
        }
    }

    public SourceLocation getCurrentLocation() {
        return currentLocation;
    }

    public void reset() {
        recordNo = 0;
        currentStartLocation = new DefaultLocation(1, 1);
        currentEndLocation = new DefaultLocation(1, 1);
        markedStartLocation = new DefaultLocation(1, 1);
        hasMarkedStartLocation = false;
    }

    public void markStartLocation() {
        if (locator != null) {
            markedStartLocation = DefaultLocation.of(
                    locator.getLineNumber(),
                    locator.getColumnNumber());
            LOGGER.trace("Marking location: {}", markedStartLocation);
            hasMarkedStartLocation = true;
        }
    }

    public void storeLocation() {
        LOGGER.trace("currentStartLocation: {}, currentEndLocation: {}, " +
                        "startLocator.getLineNo: {}, startLocator.getColNo: {}",
                currentStartLocation,
                currentEndLocation,
                locator.getLineNumber(),
                locator.getColumnNumber());

        if (storeLocations
                && locator != null
                && metaHolder != null
                && metaHolder.getMeta() != null) {

            final Location startLocation;
            final Location endLocation;

            // TODO This is all a bit grim.  The storing of locations should be done by the parsers
            //   as they understand their data better and can provide better location info when they call
            //   (start|end)Element. Not sure how that would fit with the SplitFilter splitting at different
            //   levels as you could have a split filter after a DS parser splitting at a lower level.
            if (locator instanceof DSLocator) {
                // The DSLocator has a proper start and end location for each record which is more
                // accurate than just treating the last end as the new start as it takes into account
                // line breaks.
                final DSLocator dsLocator = (DSLocator) locator;

                endLocation = DefaultLocation.of(
                        dsLocator.getRecordEndLocator().getLineNumber(),
                        dsLocator.getRecordEndLocator().getColumnNumber());
                startLocation = DefaultLocation.of(
                        dsLocator.getRecordStartLocator().getLineNumber(),
                        dsLocator.getRecordStartLocator().getColumnNumber());
            } else {
                endLocation = DefaultLocation.of(
                        locator.getLineNumber(),
                        locator.getColumnNumber());

                // TODO The location comparator test is a bit of a hack to deal with the XML
                //  fragment parser as that seems
                //  to skip forward then back, presumably where it is reading each entity.
                if (hasMarkedStartLocation
                        && (LOCATION_COMPARATOR.compare(markedStartLocation, endLocation) <= 0)) {
                    // The start location has been marked so use that
                    startLocation = DefaultLocation.of(
                            markedStartLocation.getLineNo(),
                            markedStartLocation.getColNo());
                } else {
                    // Locator can provide only one location so we have to work off out previous endLocation.
                    startLocation = DefaultLocation.of(
                            currentEndLocation.getLineNo(),
                            currentEndLocation.getColNo());
                }
            }

            // Only change if we have moved forward.
            if (currentEndLocation.getLineNo() != endLocation.getLineNo()
                    || currentEndLocation.getColNo() != endLocation.getColNo()) {

                currentStartLocation = startLocation;
                currentEndLocation = endLocation;
            }

            final TextRange highlight = new TextRange(currentStartLocation, currentEndLocation);
            final DataRange dataRange = DataRange.between(currentStartLocation, currentEndLocation);

            LOGGER.trace("Storing range: {}", highlight);

            recordNo++;
            final SourceLocation sourceLocation = SourceLocation.builder(metaHolder.getMeta().getId())
                    .withChildStreamType(metaHolder.getChildDataType())
                    .withPartNo(metaHolder.getStreamNo())
                    .withSegmentNumber(recordNo)
                    .withDataRange(dataRange)
                    .withHighlight(highlight)
                    .build();

            if (maxSize <= 1) {
                currentLocation = sourceLocation;

            } else {
                if (locations == null) {
                    locations = new ArrayList<>();
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

    public void setStoreLocations(final boolean storeLocations) {
        this.storeLocations = storeLocations;
    }

    public enum FunctionType {
        LOCATION, RECORD_NO, LINE_FROM, COL_FROM, LINE_TO, COL_TO
    }
}

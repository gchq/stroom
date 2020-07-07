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

import stroom.data.shared.DataRange;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.xml.converter.ds3.DSLocator;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Highlight;
import stroom.util.shared.Location;

import org.xml.sax.Locator;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@PipelineScoped
public class LocationHolder implements Holder {
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

    @Inject
    public LocationHolder(final MetaHolder metaHolder) {
        this.metaHolder = metaHolder;
        reset();
    }

    public void setDocumentLocator(final Locator locator, final int maxSize) {
        this.maxSize = maxSize;
        if (locator instanceof DSLocator) {
            this.locator = ((DSLocator) locator).getRecordEndLocator();
        } else {
            this.locator = locator;
        }
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
    }

    public void storeLocation() {
        if (storeLocations && locator != null && metaHolder != null && metaHolder.getMeta() != null) {
            final Highlight highlight = createHighlight();
            final DataRange dataRange = createDataRange();

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

    private Highlight createHighlight() {
        final Location location = new DefaultLocation(locator.getLineNumber(), locator.getColumnNumber());
        // Only change if we have moved forward.
        if (currentEndLocation.getLineNo() != location.getLineNo() || currentEndLocation.getColNo() != location.getColNo()) {
            currentStartLocation = currentEndLocation;
            currentEndLocation = location;
        }
        return new Highlight(currentStartLocation, currentEndLocation);
    }

    private DataRange createDataRange() {
        final Location location = new DefaultLocation(locator.getLineNumber(), locator.getColumnNumber());
        // Only change if we have moved forward.
        if (currentEndLocation.getLineNo() != location.getLineNo() || currentEndLocation.getColNo() != location.getColNo()) {
            currentStartLocation = currentEndLocation;
            currentEndLocation = location;
        }
        return DataRange.between(currentStartLocation, currentEndLocation);
    }

    public enum FunctionType {
        LOCATION, RECORD_NO, LINE_FROM, COL_FROM, LINE_TO, COL_TO
    }
}

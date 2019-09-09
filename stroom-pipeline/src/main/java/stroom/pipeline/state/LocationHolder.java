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

import stroom.pipeline.shared.SourceLocation;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.List;

@PipelineScoped
public class LocationHolder implements Holder {
    private List<SourceLocation> locations;
    private SourceLocation currentLocation;
    private FunctionType functionType;

    public List<SourceLocation> getLocations() {
        return locations;
    }

    public void setLocations(final List<SourceLocation> locations) {
        this.locations = locations;
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

    public void setCurrentLocation(final SourceLocation currentLocation) {
        this.currentLocation = currentLocation;
    }

    public enum FunctionType {
        LOCATION, RECORD_NO, LINE_FROM, COL_FROM, LINE_TO, COL_TO
    }
}

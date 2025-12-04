/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline;

import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.Location;

import org.xml.sax.Locator;

import javax.xml.transform.SourceLocator;

@PipelineScoped
public class LocationFactoryProxy implements LocationFactory {

    private LocationFactory locationFactory;

    @Override
    public Location create(final int colNo, final int lineNo) {
        if (locationFactory == null) {
            return null;
        }
        return locationFactory.create(colNo, lineNo);
    }

    @Override
    public Location create() {
        if (locationFactory == null) {
            return null;
        }
        return locationFactory.create();
    }

    public Location create(final Locator locator) {
        if (locator == null) {
            return create();
        }

        return create(locator.getLineNumber(), locator.getColumnNumber());
    }

    public Location create(final SourceLocator locator) {
        if (locator == null) {
            return create();
        }

        return create(locator.getLineNumber(), locator.getColumnNumber());
    }

    public void setLocationFactory(final LocationFactory locationFactory) {
        this.locationFactory = locationFactory;
    }
}

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

package stroom.pipeline.filter;

import stroom.pipeline.factory.PipelineFactoryException;
import stroom.pipeline.factory.Target;
import stroom.util.shared.ElementId;

public final class XMLFilterForkFactory {
    private XMLFilterForkFactory() {
        // Utility class.
    }

    public static XMLFilter addTarget(final ElementId elementId, final XMLFilter existing, final Target target) {
        XMLFilter newFilter = existing;

        if (target == null) {
            throw new PipelineFactoryException("Attempt to link to a null target: " + elementId + " > NULL");
        }
        if (!(target instanceof XMLFilter)) {
            throw new PipelineFactoryException("Attempt to link to an element that is not an XMLFilter: " + elementId
                    + " > " + target.getElementId());
        }

        final XMLFilter filter = (XMLFilter) target;

        if (existing == null || existing instanceof NullXMLFilter) {
            newFilter = filter;

        } else if (!(existing instanceof XMLFilterFork)) {
            // If the current filter is not a fork then create a fork to the
            // existing target filter plus the new one.
            final XMLFilter[] filters = new XMLFilter[2];
            filters[0] = existing;
            filters[1] = filter;
            newFilter = new XMLFilterFork(filters);

        } else {
            // If the current filter is an instance of a fork then create a new
            // fork that uses all the existing target filters plus the new one.
            final XMLFilterFork fork = (XMLFilterFork) existing;
            final XMLFilter[] filters = new XMLFilter[fork.getFilters().length + 1];
            System.arraycopy(fork.getFilters(), 0, filters, 0, fork.getFilters().length);
            filters[filters.length - 1] = filter;
            newFilter = new XMLFilterFork(filters);
        }

        return newFilter;
    }

    public static XMLFilter setTarget(final ElementId elementId, final Target target) {
        XMLFilter newFilter = NullXMLFilter.INSTANCE;

        if (target != null) {
            if (!(target instanceof XMLFilter)) {
                throw new PipelineFactoryException("Attempt to link to an element that is not an XMLFilter: "
                        + elementId + " > " + target.getElementId());
            }

            final XMLFilter filter = (XMLFilter) target;
            newFilter = filter;
        }

        return newFilter;
    }
}

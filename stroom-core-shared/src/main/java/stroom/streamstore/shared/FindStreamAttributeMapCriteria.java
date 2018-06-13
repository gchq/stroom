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

package stroom.streamstore.shared;

import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.PageRequest;
import stroom.streamstore.meta.api.FindStreamCriteria;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced that find by
 * example.
 * </p>
 */
public class FindStreamAttributeMapCriteria extends FindNamedEntityCriteria {
    private static final long serialVersionUID = -4777723504698304778L;

    private FindStreamCriteria findStreamCriteria = null;

    public FindStreamAttributeMapCriteria() {
        findStreamCriteria = new FindStreamCriteria();
        super.setPageRequest(null);
    }

    public FindStreamCriteria getFindStreamCriteria() {
        return findStreamCriteria;
    }

    public FindStreamCriteria obtainFindStreamCriteria() {
        if (findStreamCriteria == null) {
            findStreamCriteria = new FindStreamCriteria();
        }
        return findStreamCriteria;
    }

    @Override
    public final PageRequest getPageRequest() {
        return findStreamCriteria.getPageRequest();
    }

    @Override
    public final void setPageRequest(final PageRequest pageRequest) {
        findStreamCriteria.setPageRequest(pageRequest);
    }

    @Override
    public PageRequest obtainPageRequest() {
        return findStreamCriteria.obtainPageRequest();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof FindStreamAttributeMapCriteria)) {
            return false;
        }

        final FindStreamAttributeMapCriteria other = (FindStreamAttributeMapCriteria) o;
        return this.findStreamCriteria.equals(other.findStreamCriteria);
    }

    @Override
    public int hashCode() {
        return findStreamCriteria.hashCode();
    }
}

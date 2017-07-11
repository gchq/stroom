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

package stroom.pipeline.shared;

import stroom.entity.shared.EntityMatcher;
import stroom.entity.shared.FindDocumentEntityCriteria;

import java.util.HashSet;
import java.util.Set;

import java.util.HashSet;
import java.util.Set;

public class FindPipelineEntityCriteria extends FindDocumentEntityCriteria implements EntityMatcher<PipelineEntity> {
    private static final long serialVersionUID = 1L;

    private Set<String> types;

    public FindPipelineEntityCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindPipelineEntityCriteria(final String name) {
        super(name);
    }

    public void addType(final String type) {
        if (types == null) {
            types = new HashSet<>();
        }
        types.add(type);
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(final Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean isMatch(final PipelineEntity entity) {
        if (types == null) {
            return true;
        }

        return types.contains(entity.getPipelineType());
    }
}

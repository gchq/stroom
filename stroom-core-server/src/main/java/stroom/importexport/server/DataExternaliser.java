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

package stroom.importexport.server;

import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityDependencyServiceException;

public interface DataExternaliser<E extends BaseEntity> {
    String externalise(E entity, String data);

    String internalise(E entity, String data) throws EntityDependencyServiceException;
}

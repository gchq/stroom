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

package stroom.util.shared;

/**
 * Base class of all Stroom service exceptions. These exceptions can be passed up
 * to GWT and so can unwrap all exceptions into nice messages for the user.
 */
public class EntityDependencyServiceException extends EntityServiceException {

    private static final long serialVersionUID = -6840395605715731686L;

    private String missingEntityType = null;
    private String missingEntityName = null;

    public EntityDependencyServiceException() {
    }

    public EntityDependencyServiceException(final String message) {
        super(message);
    }

    public EntityDependencyServiceException(final String missingEntityType, final String missingEntityName) {
        super("Missing dependency '" + missingEntityName + "' of type '" + missingEntityType + "'", null, false);
        this.missingEntityType = missingEntityType;
        this.missingEntityName = missingEntityName;
    }

    public String getMissingEntityName() {
        return missingEntityName;
    }

    public void setMissingEntityName(final String missingEntityName) {
        this.missingEntityName = missingEntityName;
    }

    public String getMissingEntityType() {
        return missingEntityType;
    }

    public void setMissingEntityType(final String missingEntityType) {
        this.missingEntityType = missingEntityType;
    }
}

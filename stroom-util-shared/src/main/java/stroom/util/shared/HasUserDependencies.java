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

import java.util.List;

public interface HasUserDependencies {

    /**
     * Get all known things that have a dependency on the passed userRef.
     * This method is intended to be called by another service that will ensure that
     * it filters out any items that the current user does not have VIEW permission on
     * if sending them to the UI.
     */
    List<UserDependency> getUserDependencies(final UserRef userRef);

}

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

package stroom.explorer.impl;

import stroom.util.shared.EntityServiceException;

public class NameValidationUtil {
//    public static void validate(final ProvidesNamePattern providesNamePattern, final BaseEntity entity) {
//        validate(providesNamePattern, null, entity);
//    }
//
//    public static void validate(
//    final ProvidesNamePattern providesNamePattern, final BaseEntity before, final BaseEntity after) {
//        if (after != null && after instanceof HasName) {
//            // Validate the entity name if it has been changed.
//            if (before != null && before instanceof HasName) {
//                if (!Objects.equals(((HasName) before).getName(), ((HasName) after).getName())) {
//                    validate(providesNamePattern, ((HasName) after).getName());
//                }
//            } else {
//                validate(providesNamePattern, ((HasName) after).getName());
//            }
//        }
//    }
//
//    public static void validate(final ProvidesNamePattern providesNamePattern, final String name) {
//        final String pattern = providesNamePattern.getNamePattern();
//        if (pattern != null && !pattern.isEmpty()) {
//            if (name == null || !name.matches(pattern)) {
//                throw new EntityServiceException("Invalid name \"" + name + "\" ("
//                        + pattern + ")");
//            }
//        }
//    }

    public static void validate(final String pattern, final String name) {
        if (pattern != null && !pattern.isEmpty()) {
            if (name == null || !name.matches(pattern)) {
                throw new EntityServiceException("Invalid name \"" + name + "\" ("
                        + pattern + ")");
            }
        }
    }
}

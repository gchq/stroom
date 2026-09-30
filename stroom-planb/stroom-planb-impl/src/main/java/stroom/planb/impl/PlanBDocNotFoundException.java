/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl;

/**
 * No Plan B document is registered under the requested name.
 *
 * <p>Distinct from a transient failure — a caller that resolves names per record can remember this one and
 * stop retrying it, which is why it is its own type rather than a bare {@link RuntimeException}.
 * {@code DocumentNotFoundException} cannot be used here: it carries a {@code DocRef}, which requires a type
 * and a UUID that a lookup by name does not have.
 */
public class PlanBDocNotFoundException extends RuntimeException {

    private final String name;

    public PlanBDocNotFoundException(final String name) {
        super("No Plan B doc found with name: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

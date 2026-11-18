/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.shared;

import stroom.docref.HasDisplayValue;

/// How far below the base DN an LDAP Data Connection searches.
public enum LdapSearchScope implements HasDisplayValue {
    /// The base DN's own entry only.
    BASE("Base entry only"),
    /// The entries directly below the base DN, not the base DN's own entry.
    ONE_LEVEL("One level below the base"),
    /// The base DN's entry and every entry below it.
    SUBTREE("The whole subtree");

    private final String displayValue;

    LdapSearchScope(final String displayValue) {
        this.displayValue = displayValue;
    }

    /// Gets the name of the scope as shown to users.
    ///
    /// @return The display name.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}

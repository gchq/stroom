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

package stroom.util.shared;

import stroom.docref.SharedObject;

public class SharedString implements SharedObject, Comparable<SharedString> {
    public static final SharedString EMPTY = SharedString.wrap("");
    private static final long serialVersionUID = 2999109513859666073L;
    private String string;

    public SharedString() {
        // Default constructor necessary for GWT serialisation.
    }

    private SharedString(final String string) {
        this.string = string;
    }

    public static SharedString wrap(final String string) {
        if (string == null) {
            return null;
        }
        return new SharedString(string);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj instanceof SharedString) {
            return ((SharedString) obj).string.equals(string);
        }

        return false;
    }

    @Override
    public int compareTo(final SharedString sharedString) {
        // Search results use compare and I want them to ignore case.
        return string.compareToIgnoreCase(sharedString.string);
    }

    @Override
    public String toString() {
        return string;
    }
}

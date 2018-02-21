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

package stroom.importexport;

import stroom.pipeline.shared.ExtensionProvider;

import java.io.Serializable;
import java.util.Comparator;

public class Property {
    private final String name;
    private final boolean externalFile;
    private final ExtensionProvider extensionProvider;

    public Property(final String name, final boolean externalFile, final ExtensionProvider extensionProvider) {
        this.name = name;
        this.externalFile = externalFile;
        this.extensionProvider = extensionProvider;
    }

    public String getName() {
        return name;
    }

    public boolean isExternalFile() {
        return externalFile;
    }

    public ExtensionProvider getExtensionProvider() {
        return extensionProvider;
    }

    @Override
    public String toString() {
        return name;
    }

    public static final class NameComparator implements Comparator<Property>, Serializable {
        private static final long serialVersionUID = -7544586669631049101L;

        @Override
        public int compare(final Property o1, final Property o2) {
            return o1.name.compareTo(o2.name);
        }
    }
}

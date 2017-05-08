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

package stroom.resource;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.io.InputStream;
import java.io.Serializable;

/**
 * A helper object for describing a resource.
 */
public final class NamedResource implements Serializable {
    private static final long serialVersionUID = -1512843897328526571L;

    private final String name;
    private final int version;
    private transient final InputStream stream;

    private int hashCode = -1;

    /**
     * @param name
     *            The resource name for the resource.
     * @param version
     *            The version of the resource. This is used when we are caching
     *            resources to ensure we can cache more than one version and
     *            expire old versions as needed.
     * @param stream
     *            The input stream for the resource.
     */
    public NamedResource(final String name, final int version, final InputStream stream) {
        this.name = name;
        this.version = version;
        this.stream = stream;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public InputStream getStream() {
        return stream;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NamedResource)) {
            return false;
        }
        final NamedResource namedResource = (NamedResource) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(namedResource.name, name);
        builder.append(namedResource.version, version);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        if (hashCode == -1) {
            final HashCodeBuilder builder = new HashCodeBuilder();
            builder.append(name);
            builder.append(version);
            hashCode = builder.toHashCode();
        }

        return hashCode;
    }

    @Override
    public String toString() {
        return name + "_" + version;
    }
}

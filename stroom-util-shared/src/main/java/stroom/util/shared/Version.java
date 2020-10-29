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

import java.io.Serializable;

/**
 * Wrapper for build versions
 */
public class Version implements Serializable, Comparable<Version> {
    private static final long serialVersionUID = -302774034712796288L;

    private Integer major;
    private Integer minor;
    private Integer patch;

    public Version() {
        // Default constructor necessary for GWT serialisation.
    }

    public Version(final Integer major) {
        this(major, null, null);
    }

    public Version(final Integer major, final Integer minor) {
        this(major, minor, null);
    }

    public Version(final Integer major, final Integer minor, final Integer patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static Version of(final Integer major) {
        return new Version(major, null, null);
    }

    public static Version of(final Integer major, final Integer minor) {
        return new Version(major, minor, null);
    }

    public static Version of(final Integer major, final Integer minor, final Integer patch) {
        return new Version(major, minor, patch);
    }

    public static Version parse(final String string) {
        Integer major = null;
        Integer minor = null;
        Integer patch = null;
        if (string != null) {
            final String[] parts = string.split("\\.");
            if (parts.length > 0) {
                major = Integer.parseInt(parts[0]);
            }
            if (parts.length > 1) {
                minor = Integer.parseInt(parts[1]);
            }
            if (parts.length > 2) {
                patch = Integer.parseInt(parts[2]);
            }
        }
        return new Version(major, minor, patch);
    }

    public boolean gt(final Version version) {
        final int[] a = toArray(this);
        final int[] b = toArray(version);

        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) {
                return true;
            } else if (a[i] < b[i]) {
                return false;
            }
        }

        return false;
    }

    public boolean lt(final Version version) {
        final int[] a = toArray(this);
        final int[] b = toArray(version);

        for (int i = 0; i < a.length; i++) {
            if (a[i] < b[i]) {
                return true;
            } else if (a[i] > b[i]) {
                return false;
            }
        }

        return false;
    }

    private int[] toArray(final Version v) {
        return new int[]{v.major != null ? v.major : 0, v.minor != null ? v.minor : 0, v.patch != null ? v.patch : 0};
    }

    public Integer getMajor() {
        return major;
    }

    public void setMajor(Integer major) {
        this.major = major;
    }

    public Integer getMinor() {
        return minor;
    }

    public void setMinor(Integer minor) {
        this.minor = minor;
    }

    public Integer getPatch() {
        return patch;
    }

    public void setPatch(Integer patch) {
        this.patch = patch;
    }

    @Override
    public String toString() {
        if (minor == null && patch == null) {
            return major.toString();
        } else if (patch == null) {
            return major + "." + minor;
        } else {
            return major + "." + minor + "." + patch;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Version)) {
            return false;
        }
        Version other = (Version) obj;
        EqualsBuilder equalsBuilder = new EqualsBuilder();
        equalsBuilder.append(this.major, other.major);
        equalsBuilder.append(this.minor, other.minor);
        equalsBuilder.append(this.patch, other.patch);
        return equalsBuilder.isEquals();
    }

    @Override
    public int compareTo(Version other) {
        int result = compare(this.major, other.major);
        if (result != 0) {
            return result;
        }
        result = compare(this.minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = compare(this.patch, other.patch);

        return result;
    }

    private int compare(Integer i1, Integer i2) {
        if (i1 == null) {
            i1 = 0;
        }
        if (i2 == null) {
            i2 = 0;
        }
        return i1.compareTo(i2);
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(major);
        hashCodeBuilder.append(minor);
        hashCodeBuilder.append(patch);
        return hashCodeBuilder.toHashCode();
    }
}

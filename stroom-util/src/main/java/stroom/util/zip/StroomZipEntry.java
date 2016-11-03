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

package stroom.util.zip;

public class StroomZipEntry {
    private final String baseName;
    private final String fullName;
    private final StroomZipFileType stroomZipFileType;

    public StroomZipEntry(String fullName, final String baseName, final StroomZipFileType stroomZipFileType) {
        this.baseName = baseName;
        this.stroomZipFileType = stroomZipFileType;
        if (fullName == null && baseName != null && stroomZipFileType != null) {
            this.fullName = baseName + stroomZipFileType.getExtension();
        } else {
            this.fullName = fullName;
        }
    }

    public boolean equalsBaseName(StroomZipEntry other) {
        if (this.baseName == null && other.baseName == null) {
            return false;
        }
        if (this.baseName == null) {
            return this.fullName.startsWith(other.baseName);
        }
        if (other.baseName == null) {
            return other.fullName.startsWith(this.baseName);
        }
        return this.baseName.equals(other.baseName);
    }

    public String getBaseName() {
        return baseName;
    }

    public StroomZipFileType getStroomZipFileType() {
        return stroomZipFileType;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return fullName;
    }
}

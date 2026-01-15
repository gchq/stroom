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

package stroom.lmdb;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.ValidFilePath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class LmdbLibraryConfig extends AbstractConfig implements IsStroomConfig {

    static final String SYSTEM_LIBRARY_PATH_PROP_NAME = "providedSystemLibraryPath";
    static final String EXTRACT_DIR_PROP_NAME = "systemLibraryExtractDir";
    static final String DEFAULT_LIBRARY_EXTRACT_SUB_DIR_NAME = "lmdb_library";

    // These are dups of org.lmdbjava.Library.LMDB_* but that class is pkg private for some reason.
    public static final String LMDB_EXTRACT_DIR_PROP = "lmdbjava.extract.dir";
    public static final String LMDB_NATIVE_LIB_PROP = "lmdbjava.native.lib";

    private final String providedSystemLibraryPath;
    private final String systemLibraryExtractDir;

    public LmdbLibraryConfig() {
        providedSystemLibraryPath = null;
        systemLibraryExtractDir = DEFAULT_LIBRARY_EXTRACT_SUB_DIR_NAME;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public LmdbLibraryConfig(@JsonProperty(SYSTEM_LIBRARY_PATH_PROP_NAME) final String providedSystemLibraryPath,
                             @JsonProperty(EXTRACT_DIR_PROP_NAME) final String systemLibraryExtractDir) {
        this.providedSystemLibraryPath = providedSystemLibraryPath;
        this.systemLibraryExtractDir = systemLibraryExtractDir;
    }

    @ValidFilePath
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonProperty(SYSTEM_LIBRARY_PATH_PROP_NAME)
    @JsonPropertyDescription("The path to a provided LMDB system library file. If unset the LMDB binary " +
            "bundled with Stroom will be extracted to 'systemLibraryExtractDir'. This property can be used if " +
            "you already have LMDB installed or want to make use of a package manager provided instance. " +
            "If you set this property care needs  to be taken over version compatibility between the version " +
            "of LMDBJava (that Stroom uses to interact with LMDB) and the version of the LMDB binary.")
    public String getProvidedSystemLibraryPath() {
        return providedSystemLibraryPath;
    }

    @RequiresRestart(RestartScope.SYSTEM)
    @JsonProperty(EXTRACT_DIR_PROP_NAME)
    @JsonPropertyDescription("The directory to extract the bundled LMDB system library to. Only used if " +
            "property providedSystemLibraryPath is not set. On boot Stroom will extract the LMDB binary to this " +
            "location. It will also delete old copies of the LMDB system library if found.")
    public String getSystemLibraryExtractDir() {
        return systemLibraryExtractDir;
    }

    @Override
    public String toString() {
        return "LmdbLibraryConfig{" +
                "providedSystemLibraryPath='" + providedSystemLibraryPath + '\'' +
                ", systemLibraryExtractDir='" + systemLibraryExtractDir + '\'' +
                '}';
    }

    public LmdbLibraryConfig withSystemLibraryExtractDir(final String systemLibraryExtractDir) {
        return new LmdbLibraryConfig(providedSystemLibraryPath, systemLibraryExtractDir);
    }
}

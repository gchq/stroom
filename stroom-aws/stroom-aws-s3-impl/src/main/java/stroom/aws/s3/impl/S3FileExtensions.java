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

package stroom.aws.s3.impl;

import stroom.data.shared.StreamTypeNames;

import java.util.HashMap;
import java.util.Map;

public class S3FileExtensions {
    public static final String MANIFEST_FILE_NAME = "001.mf";
    public static final String ZIP_FILE_NAME = "temp.zip";
    public static final String DATA_EXTENSION = ".dat";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String INDEX_EXTENSION = ".idx";
    public static final String META_EXTENSION = ".meta";
    public static final String CONTEXT_EXTENSION = ".ctx";
    public static final Map<String, String> EXTENSION_MAP = new HashMap<>();

    static {
        EXTENSION_MAP.put(StreamTypeNames.META, META_EXTENSION);
        EXTENSION_MAP.put(StreamTypeNames.CONTEXT, CONTEXT_EXTENSION);
    }

}

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

package stroom.streamstore.shared;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StreamAttributeConstants {
    public static final String REC_READ = "RecRead";
    public static final String REC_WRITE = "RecWrite";
    public static final String REC_INFO = "RecInfo";
    public static final String REC_WARN = "RecWarn";
    public static final String REC_ERROR = "RecError";
    public static final String REC_FATAL = "RecFatal";
    public static final String DURATION = "Duration";
    public static final String NODE = "Node";
    public static final String FEED = "Feed";
    public static final String STREAM_ID = "StreamId";
    public static final String PARENT_STREAM_ID = "ParentStreamId";
    public static final String FILE_SIZE = "FileSize";
    public static final String STREAM_SIZE = "StreamSize";
    public static final String STREAM_TYPE = "StreamType";
    public static final String CREATE_TIME = "CreateTime";
    public static final String EFFECTIVE_TIME = "EffectiveTime";

    public static final Map<String, StreamAttributeFieldUse> SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP;

    static {
        final HashMap<String, StreamAttributeFieldUse> map = new HashMap<String, StreamAttributeFieldUse>();
        map.put(REC_READ, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(REC_WRITE, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(REC_INFO, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(REC_WARN, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(REC_ERROR, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(REC_FATAL, StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD);
        map.put(DURATION, StreamAttributeFieldUse.DURATION_FIELD);
        map.put(NODE, StreamAttributeFieldUse.FIELD);
        map.put(FEED, StreamAttributeFieldUse.FIELD);
        map.put(STREAM_ID, StreamAttributeFieldUse.ID);
        map.put(PARENT_STREAM_ID, StreamAttributeFieldUse.ID);
        map.put(FILE_SIZE, StreamAttributeFieldUse.SIZE_FIELD);
        map.put(STREAM_SIZE, StreamAttributeFieldUse.SIZE_FIELD);
        map.put(STREAM_TYPE, StreamAttributeFieldUse.FIELD);
        map.put(CREATE_TIME, StreamAttributeFieldUse.DATE_FIELD);
        map.put(EFFECTIVE_TIME, StreamAttributeFieldUse.DATE_FIELD);

        SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP = Collections.unmodifiableMap(map);

    }
}

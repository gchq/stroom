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

package stroom.util.shared.string;

import stroom.util.shared.concurrent.CopyOnWriteMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A set of static common {@link CIKey} instances
 */
public class CIKeys {

    // Hold some common keys, so we can just re-use instances rather than creating new each time
    // Some of these map values will get used statically in code, but a lot will come from dash/query
    // fields/columns/tokens which are not known at compile time so the map lets us save on
    // object creation for common ones.
    // The cost of a hashmap get is less than the combined cost of CIKey object creation and
    // the toLowerCase call.
    // These maps use CopyOnWriteMap because they are pretty much write once, ready MANY
    // so are as close to a hashmap as we can get
    private static final Map<String, CIKey> KEY_TO_COMMON_CIKEY_MAP;
    private static final Map<String, CIKey> LOWER_KEY_TO_COMMON_CIKEY_MAP;

    public static final CIKey EMPTY;

    // Upper camel case keys
    public static final CIKey ACCOUNT_ID;
    public static final CIKey ACCOUNT_NAME;
    public static final CIKey CLASSIFICATION;
    public static final CIKey CONTEXT_FORMAT;
    public static final CIKey CONTEXT_ENCODING;
    public static final CIKey COMPONENT;
    public static final CIKey COMPRESSION;
    public static final CIKey DURATION;
    public static final CIKey EFFECTIVE_TIME;
    public static final CIKey ENCODING;
    public static final CIKey END;
    public static final CIKey ENVIRONMENT;
    public static final CIKey EVENT_ID;
    public static final CIKey EVENT_TIME;
    public static final CIKey FEED;
    public static final CIKey FORMAT;
    public static final CIKey FORWARD_ERROR;
    public static final CIKey FILES;
    public static final CIKey GUID;
    public static final CIKey ID;
    public static final CIKey INDEX;
    public static final CIKey INSERT_TIME;
    public static final CIKey KEY;
    public static final CIKey KEY_END;
    public static final CIKey KEY_START;
    public static final CIKey NAME;
    public static final CIKey NODE;
    public static final CIKey PARTITION;
    public static final CIKey PIPELINE;
    public static final CIKey PROXY_FORWARD_ID;
    public static final CIKey RECEIVED_PATH;
    public static final CIKey RECEIVED_TIME;
    public static final CIKey RECEIVED_TIME_HISTORY;
    public static final CIKey REMOTE_ADDRESS;
    public static final CIKey REMOTE_CERT_EXPIRY;
    public static final CIKey REMOTE_FILE;
    public static final CIKey REMOTE_HOST;
    public static final CIKey REMOTE_DN;
    public static final CIKey SCHEMA;
    public static final CIKey SCHEMA_VERSION;
    public static final CIKey START;
    public static final CIKey STATUS;
    public static final CIKey STREAM_ID;
    public static final CIKey STREAM_SIZE;
    public static final CIKey SUBJECT;
    public static final CIKey SYSTEM;
    public static final CIKey TERMINAL;
    public static final CIKey TIME;
    public static final CIKey TITLE;
    public static final CIKey TYPE;
    public static final CIKey UPLOAD_USERNAME;
    public static final CIKey UPLOAD_USER_ID;
    public static final CIKey USER;
    public static final CIKey UUID;
    public static final CIKey VALUE;
    public static final CIKey VALUE_TYPE;

    // Lower case keys
    public static final CIKey ACCEPT;
    public static final CIKey CONNECTION;
    public static final CIKey EXPECT;

    // kebab case keys
    public static final CIKey CONTENT___ENCODING;
    public static final CIKey CONTENT___LENGTH;
    public static final CIKey TRANSFER___ENCODING;
    public static final CIKey USER___AGENT;
    public static final CIKey X___FORWARDED___FOR;

    // Upper sentence case keys
    public static final CIKey ANALYTIC__RULE;
    public static final CIKey CREATE__TIME;
    public static final CIKey CREATE__TIME__MS;
    public static final CIKey DOC__COUNT;
    public static final CIKey EFFECTIVE__TIME;
    public static final CIKey END__TIME;
    public static final CIKey END__TIME__MS;
    public static final CIKey ERROR__COUNT;
    public static final CIKey FATAL__ERROR__COUNT;
    public static final CIKey FILE__SIZE;
    public static final CIKey INDEX__NAME;
    public static final CIKey INFO__COUNT;
    public static final CIKey LAST__COMMIT;
    public static final CIKey META__ID;
    public static final CIKey PARENT__CREATE__TIME;
    public static final CIKey PARENT__FEED;
    public static final CIKey PARENT__ID;
    public static final CIKey PARENT__STATUS;
    public static final CIKey PIPELINE__NAME;
    public static final CIKey PROCESSOR__DELETED;
    public static final CIKey PROCESSOR__ENABLED;
    public static final CIKey PROCESSOR__FILTER__DELETED;
    public static final CIKey PROCESSOR__FILTER__ENABLED;
    public static final CIKey PROCESSOR__FILTER__ID;
    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS;
    public static final CIKey PROCESSOR__FILTER__PRIORITY;
    public static final CIKey PROCESSOR__FILTER__UUID;
    public static final CIKey PROCESSOR__ID;
    public static final CIKey PROCESSOR__PIPELINE;
    public static final CIKey PROCESSOR__TASK__ID;
    public static final CIKey PROCESSOR__TYPE;
    public static final CIKey PROCESSOR__UUID;
    public static final CIKey RAW__SIZE;
    public static final CIKey READ__COUNT;
    public static final CIKey START__TIME;
    public static final CIKey START__TIME__MS;
    public static final CIKey STATUS__TIME;
    public static final CIKey STATUS__TIME__MS;
    public static final CIKey TASK__ID;
    public static final CIKey VOLUME__GROUP;
    public static final CIKey VOLUME__PATH;
    public static final CIKey WARNING__COUNT;
    public static final CIKey WRITE__COUNT;

    // Reference Data fields
    public static final CIKey FEED__NAME;
    public static final CIKey LAST__ACCESSED__TIME;
    public static final CIKey MAP__NAME;
    public static final CIKey PART__NUMBER;
    public static final CIKey PIPELINE__VERSION;
    public static final CIKey PROCESSING__STATE;
    public static final CIKey REFERENCE__LOADER__PIPELINE;
    public static final CIKey STREAM__ID;
    public static final CIKey VALUE__REFERENCE__COUNT;

    // Annotations keys
    public static final CIKey ANNO_ASSIGNED_TO;
    public static final CIKey ANNO_COMMENT;
    public static final CIKey ANNO_CREATED_BY;
    public static final CIKey ANNO_CREATED_ON;
    public static final CIKey ANNO_HISTORY;
    public static final CIKey ANNO_ID;
    public static final CIKey ANNO_STATUS;
    public static final CIKey ANNO_SUBJECT;
    public static final CIKey ANNO_TITLE;
    public static final CIKey ANNO_UPDATED_BY;
    public static final CIKey ANNO_UPDATED_ON;

    public static final CIKey UNDERSCORE_EVENT_ID;
    public static final CIKey UNDERSCORE_STREAM_ID;
    public static final CIKey UNDERSCORE_TIME;

    static {
        // Temporary maps to capture all the entries
        final Map<String, CIKey> keyToCiKeyMap = new HashMap<>();
        final Map<String, CIKey> lowerKeyToCiKeyMap = new HashMap<>();

        final Function<String, CIKey> func = key ->
                internCommonKey(key, keyToCiKeyMap, lowerKeyToCiKeyMap);

        EMPTY = func.apply("");

        // Upper camel case keys
        ACCOUNT_ID = func.apply("AccountId");
        ACCOUNT_NAME = func.apply("AccountName");
        CLASSIFICATION = func.apply("Classification");
        CONTEXT_FORMAT = func.apply("ContextFormat");
        CONTEXT_ENCODING = func.apply("ContextEncoding");
        COMPONENT = func.apply("Component");
        COMPRESSION = func.apply("Compression");
        DURATION = func.apply("Duration");
        EFFECTIVE_TIME = func.apply("EffectiveTime");
        ENCODING = func.apply("Encoding");
        END = func.apply("End");
        ENVIRONMENT = func.apply("Environment");
        EVENT_ID = func.apply("EventId");
        EVENT_TIME = func.apply("EventTime");
        FEED = func.apply("Feed");
        FORMAT = func.apply("Format");
        FORWARD_ERROR = func.apply("ForwardError");
        FILES = func.apply("Files");
        GUID = func.apply("GUID");
        ID = func.apply("Id");
        INDEX = func.apply("Index");
        INSERT_TIME = func.apply("InsertTime");
        KEY = func.apply("Key");
        KEY_END = func.apply("KeyEnd");
        KEY_START = func.apply("KeyStart");
        NAME = func.apply("Name");
        NODE = func.apply("Node");
        PARTITION = func.apply("Partition");
        PIPELINE = func.apply("Pipeline");
        PROXY_FORWARD_ID = func.apply("ProxyForwardId");
        RECEIVED_PATH = func.apply("ReceivedPath");
        RECEIVED_TIME = func.apply("ReceivedTime");
        RECEIVED_TIME_HISTORY = func.apply("ReceivedTimeHistory");
        REMOTE_ADDRESS = func.apply("RemoteAddress");
        REMOTE_CERT_EXPIRY = func.apply("RemoteCertExpiry");
        REMOTE_FILE = func.apply("RemoteFile");
        REMOTE_HOST = func.apply("RemoteHost");
        REMOTE_DN = func.apply("RemoteDn");
        SCHEMA = func.apply("Schema");
        SCHEMA_VERSION = func.apply("SchemaVersion");
        START = func.apply("Start");
        STATUS = func.apply("Status");
        STREAM_ID = func.apply("StreamId");
        STREAM_SIZE = func.apply("StreamSize");
        SUBJECT = func.apply("Subject");
        SYSTEM = func.apply("System");
        TERMINAL = func.apply("Terminal");
        TIME = func.apply("Time");
        TITLE = func.apply("Title");
        TYPE = func.apply("Type");
        UPLOAD_USERNAME = func.apply("UploadUsername");
        UPLOAD_USER_ID = func.apply("UploadUserId");
        USER = func.apply("User");
        UUID = func.apply("UUID");
        VALUE = func.apply("Value");
        VALUE_TYPE = func.apply("ValueType");

        // Lower case keys
        ACCEPT = func.apply("accept");
        CONNECTION = func.apply("connection");
        EXPECT = func.apply("expect");

        // kebab case keys
        CONTENT___ENCODING = func.apply("content-encoding");
        CONTENT___LENGTH = func.apply("content-length");
        TRANSFER___ENCODING = func.apply("transfer-encoding");
        USER___AGENT = func.apply("user-agent");
        X___FORWARDED___FOR = func.apply("X-Forwarded-For");

        // Upper sentence case keys
        ANALYTIC__RULE = func.apply("Analytic Rule");
        CREATE__TIME = func.apply("Create Time");
        CREATE__TIME__MS = func.apply("Create Time Ms");
        DOC__COUNT = func.apply("Doc Count");
        EFFECTIVE__TIME = func.apply("Effective Time");
        END__TIME = func.apply("End Time");
        END__TIME__MS = func.apply("End Time Ms");
        ERROR__COUNT = func.apply("Error Count");
        FATAL__ERROR__COUNT = func.apply("Fatal Error Count");
        FILE__SIZE = func.apply("File Size");
        INDEX__NAME = func.apply("Index Name");
        INFO__COUNT = func.apply("Info Count");
        LAST__COMMIT = func.apply("Last Commit");
        META__ID = func.apply("Meta Id");
        PARENT__CREATE__TIME = func.apply("Parent Create Time");
        PARENT__FEED = func.apply("Parent Feed");
        PARENT__ID = func.apply("Parent Id");
        PARENT__STATUS = func.apply("Parent Status");
        PIPELINE__NAME = func.apply("Pipeline Name");
        PROCESSOR__DELETED = func.apply("Processor Deleted");
        PROCESSOR__ENABLED = func.apply("Processor Enabled");
        PROCESSOR__FILTER__DELETED = func.apply("Processor Filter Deleted");
        PROCESSOR__FILTER__ENABLED = func.apply("Processor Filter Enabled");
        PROCESSOR__FILTER__ID = func.apply("Processor Filter Id");
        PROCESSOR__FILTER__LAST__POLL__MS = func.apply("Processor Filter Last Poll Ms");
        PROCESSOR__FILTER__PRIORITY = func.apply("Processor Filter Priority");
        PROCESSOR__FILTER__UUID = func.apply("Processor Filter UUID");
        PROCESSOR__ID = func.apply("Processor Id");
        PROCESSOR__PIPELINE = func.apply("Processor Pipeline");
        PROCESSOR__TASK__ID = func.apply("Processor Task Id");
        PROCESSOR__TYPE = func.apply("Processor Type");
        PROCESSOR__UUID = func.apply("Processor UUID");
        RAW__SIZE = func.apply("Raw Size");
        READ__COUNT = func.apply("Read Count");
        START__TIME = func.apply("Start Time");
        START__TIME__MS = func.apply("Start Time Ms");
        STATUS__TIME = func.apply("Status Time");
        STATUS__TIME__MS = func.apply("Status Time Ms");
        TASK__ID = func.apply("Task Id");
        VOLUME__GROUP = func.apply("Volume Group");
        VOLUME__PATH = func.apply("Volume Path");
        WARNING__COUNT = func.apply("Warning Count");
        WRITE__COUNT = func.apply("Write Count");

        // Reference Data fields
        FEED__NAME = func.apply("Feed Name");
        LAST__ACCESSED__TIME = func.apply("Last Accessed Time");
        MAP__NAME = func.apply("Map Name");
        PART__NUMBER = func.apply("Part Number");
        PIPELINE__VERSION = func.apply("Pipeline Version");
        PROCESSING__STATE = func.apply("Processing State");
        REFERENCE__LOADER__PIPELINE = func.apply("Reference Loader Pipeline");
        STREAM__ID = func.apply("Stream ID");
        VALUE__REFERENCE__COUNT = func.apply("Value Reference Count");

        // Annotations keys
        ANNO_ASSIGNED_TO = func.apply("annotation:AssignedTo");
        ANNO_COMMENT = func.apply("annotation:Comment");
        ANNO_CREATED_BY = func.apply("annotation:CreatedBy");
        ANNO_CREATED_ON = func.apply("annotation:CreatedOn");
        ANNO_HISTORY = func.apply("annotation:History");
        ANNO_ID = func.apply("annotation:Id");
        ANNO_STATUS = func.apply("annotation:Status");
        ANNO_SUBJECT = func.apply("annotation:Subject");
        ANNO_TITLE = func.apply("annotation:Title");
        ANNO_UPDATED_BY = func.apply("annotation:UpdatedBy");
        ANNO_UPDATED_ON = func.apply("annotation:UpdatedOn");

        UNDERSCORE_EVENT_ID = func.apply("__event_id__");
        UNDERSCORE_STREAM_ID = func.apply("__stream_id__");
        UNDERSCORE_TIME = func.apply("__time__");

        // Now populate our master maps
        // We have to split the declaration and the assignment to avoid a full map copy for each key.
        // This way we only have one copy.
        KEY_TO_COMMON_CIKEY_MAP = new CopyOnWriteMap<>(keyToCiKeyMap);
        LOWER_KEY_TO_COMMON_CIKEY_MAP = new CopyOnWriteMap<>(lowerKeyToCiKeyMap);
    }

    private CIKeys() {
    }

    /**
     * Gets a statically held common {@link CIKey} instance that matches key (case-sensitive,
     * so that the returned {@link CIKey} will have the same original case as key).
     *
     * @return A common {@link CIKey} instance, if there is one, else null.
     */
    public static CIKey getCommonKey(final String key) {
        if (key == null) {
            return null;
        } else {
            return KEY_TO_COMMON_CIKEY_MAP.get(key);
        }
    }

    /**
     * Gets a statically held common {@link CIKey} instance whose lower-case form
     * matches lowerCaseKey.
     *
     * @return A common {@link CIKey} instance, if there is one, else null.
     */
    public static CIKey getCommonKeyByLowerCase(final String lowerCaseKey) {
        if (lowerCaseKey == null) {
            return null;
        } else {
            return LOWER_KEY_TO_COMMON_CIKEY_MAP.get(lowerCaseKey);
        }
    }

    /**
     * Add key to the map of common keys for re-use.
     * <p>
     * Only intended for use on static and commonly used {@link CIKey} instances due to the cost of
     * string interning and storage.
     * </p>
     */
    static CIKey internCommonKey(final String key) {
        return internCommonKey(key, KEY_TO_COMMON_CIKEY_MAP, LOWER_KEY_TO_COMMON_CIKEY_MAP);
    }

    private static CIKey internCommonKey(final String key,
                                         final Map<String, CIKey> keyToCiKeyMap,
                                         final Map<String, CIKey> lowerKeyToCiKeyMap) {
        final CIKey ciKey;

        if (key == null) {
            // Not interning null, so just return null
            return null;
        } else {
            // Someone else may have already interned this key so check first
            final CIKey existingCIKey = keyToCiKeyMap.get(key);
            if (existingCIKey != null) {
                // Already interned
                ciKey = existingCIKey;
            } else {
                if (key.isEmpty()) {
                    ciKey = addCommonKey(CIKey.EMPTY_STRING, keyToCiKeyMap, lowerKeyToCiKeyMap);
                } else {
                    // Ensure we are using string pool instances for both to cut
                    // down on strings held in memory
                    final String k = key.intern();
                    ciKey = addCommonKey(
                            new CIKey(k, CIKey.toLowerCase(k).intern()),
                            keyToCiKeyMap,
                            lowerKeyToCiKeyMap);
                }
            }
        }
        return ciKey;
    }

    /**
     * For test use
     */
    static CIKey addCommonKey(final CIKey ciKey) {
        return addCommonKey(ciKey, KEY_TO_COMMON_CIKEY_MAP, LOWER_KEY_TO_COMMON_CIKEY_MAP);
    }

    private static CIKey addCommonKey(final CIKey ciKey,
                                      final Map<String, CIKey> keyToCiKeyMap,
                                      final Map<String, CIKey> lowerKeyToCiKeyMap) {
        // recheck under lock, so we can be sure the two maps contain the same instance
        CIKey commonCiKey = keyToCiKeyMap.get(ciKey.get());
        if (commonCiKey == null) {

            // Add it to our static maps, so we can get a common CIKey either from its
            // exact case or its lower case form.
            keyToCiKeyMap.put(ciKey.get(), ciKey);
            lowerKeyToCiKeyMap.put(ciKey.getAsLowerCase(), ciKey);
            commonCiKey = ciKey;
        }
        return commonCiKey;
    }

    /**
     * For testing use only
     */
    static synchronized void clearCommonKeys() {
        KEY_TO_COMMON_CIKEY_MAP.clear();
        LOWER_KEY_TO_COMMON_CIKEY_MAP.clear();
    }

    /**
     * @return The set of all common {@link CIKey}s
     */
    static Set<CIKey> commonKeys() {
        //noinspection Java9CollectionFactory // GWT :-(
        return Collections.unmodifiableSet(new HashSet<>(KEY_TO_COMMON_CIKEY_MAP.values()));
    }
}

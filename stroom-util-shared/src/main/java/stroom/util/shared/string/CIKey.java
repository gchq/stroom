/*
 * Copyright 2024 Crown Copyright
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

import stroom.util.shared.GwtNullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A wrapper for a {@link String} whose {@link CIKey#equals(Object)} and
 * {@link CIKey#hashCode()} methods are performed on the lower-case
 * form of {@code key}.
 * <p>
 * Useful as a case-insensitive cache key that retains the case of the
 * original string at the cost of wrapping it in another object.
 * </p>
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class CIKey implements Comparable<CIKey> {

    public static final CIKey NULL_STRING = new CIKey(null);
    public static final CIKey EMPTY_STRING = new CIKey("");

    private static final Comparator<CIKey> COMPARATOR = Comparator.comparing(CIKey::getAsLowerCase);

    // Upper camel case keys
//    public static final CIKey COMPRESSION = CIKey.commonKey("Compression");
//    public static final CIKey DURATION = CIKey.commonKey("Duration");
//    public static final CIKey EFFECTIVE_TIME = CIKey.commonKey("EffectiveTime");
//    public static final CIKey END = CIKey.commonKey("End");
//    public static final CIKey EVENT_ID = CIKey.commonKey("EventId");
//    public static final CIKey EVENT_TIME = CIKey.commonKey("EventTime");
//    public static final CIKey FEED = CIKey.commonKey("Feed");
//    public static final CIKey GUID = CIKey.commonKey("GUID");
//    public static final CIKey ID = CIKey.commonKey("Id");
//    public static final CIKey INDEX = CIKey.commonKey("Index");
//    public static final CIKey INSERT_TIME = CIKey.commonKey("InsertTime");
//    public static final CIKey KEY = CIKey.commonKey("Key");
//    public static final CIKey KEY_END = CIKey.commonKey("KeyEnd");
//    public static final CIKey KEY_START = CIKey.commonKey("KeyStart");
//    public static final CIKey NAME = CIKey.commonKey("Name");
//    public static final CIKey NODE = CIKey.commonKey("Node");
//    public static final CIKey PARTITION = CIKey.commonKey("Partition");
//    public static final CIKey PIPELINE = CIKey.commonKey("Pipeline");
//    public static final CIKey RECEIVED_PATH = CIKey.commonKey("ReceivedPath");
//    public static final CIKey RECEIVED_TIME = CIKey.commonKey("ReceivedTime");
//    public static final CIKey RECEIVED_TIME_HISTORY = CIKey.commonKey("ReceivedTimeHistory");
//    public static final CIKey REMOTE_ADDRESS = CIKey.commonKey("RemoteAddress");
//    public static final CIKey REMOTE_CERT_EXPIRY = CIKey.commonKey("RemoteCertExpiry");
//    public static final CIKey REMOTE_FILE = CIKey.commonKey("RemoteFile");
//    public static final CIKey REMOTE_HOST = CIKey.commonKey("RemoteHost");
//    public static final CIKey START = CIKey.commonKey("Start");
//    public static final CIKey STATUS = CIKey.commonKey("Status");
//    public static final CIKey STREAM_ID = CIKey.commonKey("StreamId");
//    public static final CIKey STREAM_SIZE = CIKey.commonKey("StreamSize");
//    public static final CIKey SUBJECT = CIKey.commonKey("Subject");
//    public static final CIKey TERMINAL = CIKey.commonKey("Terminal");
//    public static final CIKey TIME = CIKey.commonKey("Time");
//    public static final CIKey TITLE = CIKey.commonKey("Title");
//    public static final CIKey TYPE = CIKey.commonKey("Type");
//    public static final CIKey UPLOAD_USERNAME = CIKey.commonKey("UploadUsername");
//    public static final CIKey UPLOAD_USER_ID = CIKey.commonKey("UploadUserId");
//    public static final CIKey UUID = CIKey.commonKey("UUID");
//    public static final CIKey VALUE = CIKey.commonKey("Value");
//    public static final CIKey VALUE_TYPE = CIKey.commonKey("ValueType");
//
//    // Lower case keys
//    public static final CIKey ACCEPT = CIKey.commonKey("accept");
//    public static final CIKey CONNECTION = CIKey.commonKey("connection");
//    public static final CIKey EXPECT = CIKey.commonKey("expect");
//
//    // kebab case keys
//    public static final CIKey CONTENT___ENCODING = CIKey.commonKey("content-encoding");
//    public static final CIKey CONTENT___LENGTH = CIKey.commonKey("content-length");
//    public static final CIKey TRANSFER___ENCODING = CIKey.commonKey("transfer-encoding");
//    public static final CIKey USER___AGENT = CIKey.commonKey("user-agent");
//    public static final CIKey X___FORWARDED___FOR = CIKey.commonKey("X-Forwarded-For");
//
//    // Upper sentence case keys
//    public static final CIKey ANALYTIC__RULE = CIKey.commonKey("Analytic Rule");
//    public static final CIKey CREATE__TIME = CIKey.commonKey("Create Time");
//    public static final CIKey CREATE__TIME__MS = CIKey.commonKey("Create Time Ms");
//    public static final CIKey DOC__COUNT = CIKey.commonKey("Doc Count");
//    public static final CIKey EFFECTIVE__TIME = CIKey.commonKey("Effective Time");
//    public static final CIKey END__TIME = CIKey.commonKey("End Time");
//    public static final CIKey END__TIME__MS = CIKey.commonKey("End Time Ms");
//    public static final CIKey ERROR__COUNT = CIKey.commonKey("Error Count");
//    public static final CIKey FATAL__ERROR__COUNT = CIKey.commonKey("Fatal Error Count");
//    public static final CIKey FILE__SIZE = CIKey.commonKey("File Size");
//    public static final CIKey INDEX__NAME = CIKey.commonKey("Index Name");
//    public static final CIKey INFO__COUNT = CIKey.commonKey("Info Count");
//    public static final CIKey LAST__COMMIT = CIKey.commonKey("Last Commit");
//    public static final CIKey META__ID = CIKey.commonKey("Meta Id");
//    public static final CIKey PARENT__CREATE__TIME = CIKey.commonKey("Parent Create Time");
//    public static final CIKey PARENT__FEED = CIKey.commonKey("Parent Feed");
//    public static final CIKey PARENT__ID = CIKey.commonKey("Parent Id");
//    public static final CIKey PARENT__STATUS = CIKey.commonKey("Parent Status");
//    public static final CIKey PIPELINE__NAME = CIKey.commonKey("Pipeline Name");
//    public static final CIKey PROCESSOR__DELETED = CIKey.commonKey("Processor Deleted");
//    public static final CIKey PROCESSOR__ENABLED = CIKey.commonKey("Processor Enabled");
//    public static final CIKey PROCESSOR__FILTER__DELETED = CIKey.commonKey("Processor Filter Deleted");
//    public static final CIKey PROCESSOR__FILTER__ENABLED = CIKey.commonKey("Processor Filter Enabled");
//    public static final CIKey PROCESSOR__FILTER__ID = CIKey.commonKey("Processor Filter Id");
//    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS = CIKey.commonKey("Processor Filter Last Poll Ms");
//    public static final CIKey PROCESSOR__FILTER__PRIORITY = CIKey.commonKey("Processor Filter Priority");
//    public static final CIKey PROCESSOR__FILTER__UUID = CIKey.commonKey("Processor Filter UUID");
//    public static final CIKey PROCESSOR__ID = CIKey.commonKey("Processor Id");
//    public static final CIKey PROCESSOR__PIPELINE = CIKey.commonKey("Processor Pipeline");
//    public static final CIKey PROCESSOR__TASK__ID = CIKey.commonKey("Processor Task Id");
//    public static final CIKey PROCESSOR__TYPE = CIKey.commonKey("Processor Type");
//    public static final CIKey PROCESSOR__UUID = CIKey.commonKey("Processor UUID");
//    public static final CIKey RAW__SIZE = CIKey.commonKey("Raw Size");
//    public static final CIKey READ__COUNT = CIKey.commonKey("Read Count");
//    public static final CIKey START__TIME = CIKey.commonKey("Start Time");
//    public static final CIKey START__TIME__MS = CIKey.commonKey("Start Time Ms");
//    public static final CIKey STATUS__TIME = CIKey.commonKey("Status Time");
//    public static final CIKey STATUS__TIME__MS = CIKey.commonKey("Status Time Ms");
//    public static final CIKey TASK__ID = CIKey.commonKey("Task Id");
//    public static final CIKey VOLUME__GROUP = CIKey.commonKey("Volume Group");
//    public static final CIKey VOLUME__PATH = CIKey.commonKey("Volume Path");
//    public static final CIKey WARNING__COUNT = CIKey.commonKey("Warning Count");
//    public static final CIKey WRITE__COUNT = CIKey.commonKey("Write Count");
//
//    // Reference Data fields
//    public static final CIKey FEED__NAME = CIKey.commonKey("Feed Name");
//    public static final CIKey LAST__ACCESSED__TIME = CIKey.commonKey("Last Accessed Time");
//    public static final CIKey MAP__NAME = CIKey.commonKey("Map Name");
//    public static final CIKey PART__NUMBER = CIKey.commonKey("Part Number");
//    public static final CIKey PIPELINE__VERSION = CIKey.commonKey("Pipeline Version");
//    public static final CIKey PROCESSING__STATE = CIKey.commonKey("Processing State");
//    public static final CIKey REFERENCE__LOADER__PIPELINE = CIKey.commonKey("Reference Loader Pipeline");
//    public static final CIKey STREAM__ID = CIKey.commonKey("Stream ID");
//    public static final CIKey VALUE__REFERENCE__COUNT = CIKey.commonKey("Value Reference Count");
//
//    // Annotations keys
//    public static final CIKey ANNO_ASSIGNED_TO = CIKey.commonKey("annotation:AssignedTo");
//    public static final CIKey ANNO_COMMENT = CIKey.commonKey("annotation:Comment");
//    public static final CIKey ANNO_CREATED_BY = CIKey.commonKey("annotation:CreatedBy");
//    public static final CIKey ANNO_CREATED_ON = CIKey.commonKey("annotation:CreatedOn");
//    public static final CIKey ANNO_HISTORY = CIKey.commonKey("annotation:History");
//    public static final CIKey ANNO_ID = CIKey.commonKey("annotation:Id");
//    public static final CIKey ANNO_STATUS = CIKey.commonKey("annotation:Status");
//    public static final CIKey ANNO_SUBJECT = CIKey.commonKey("annotation:Subject");
//    public static final CIKey ANNO_TITLE = CIKey.commonKey("annotation:Title");
//    public static final CIKey ANNO_UPDATED_BY = CIKey.commonKey("annotation:UpdatedBy");
//    public static final CIKey ANNO_UPDATED_ON = CIKey.commonKey("annotation:UpdatedOn");
//
//    public static final CIKey UNDERSCORE_EVENT_ID = CIKey.commonKey("__event_id__");
//    public static final CIKey UNDERSCORE_STREAM_ID = CIKey.commonKey("__stream_id__");
//    public static final CIKey UNDERSCORE_TIME = CIKey.commonKey("__time__");


//    public static final CIKey COMPRESSION = CIKeys.COMPRESSION;
//    public static final CIKey DURATION = CIKeys.DURATION;
//    public static final CIKey EFFECTIVE_TIME = CIKeys.EFFECTIVE_TIME;
//    public static final CIKey END = CIKeys.END;
//    public static final CIKey EVENT_ID = CIKeys.EVENT_ID;
//    public static final CIKey EVENT_TIME = CIKeys.EVENT_TIME;
//    public static final CIKey FEED = CIKeys.FEED;
//    public static final CIKey GUID = CIKeys.GUID;
//    public static final CIKey ID = CIKeys.ID;
//    public static final CIKey INDEX = CIKeys.INDEX;
//    public static final CIKey INSERT_TIME = CIKeys.INSERT_TIME;
//    public static final CIKey KEY = CIKeys.KEY;
//    public static final CIKey KEY_END = CIKeys.KEY_END;
//    public static final CIKey KEY_START = CIKeys.KEY_START;
//    public static final CIKey NAME = CIKeys.NAME;
//    public static final CIKey NODE = CIKeys.NODE;
//    public static final CIKey PARTITION = CIKeys.PARTITION;
//    public static final CIKey PIPELINE = CIKeys.PIPELINE;
//    public static final CIKey RECEIVED_PATH = CIKeys.RECEIVED_PATH;
//    public static final CIKey RECEIVED_TIME = CIKeys.RECEIVED_TIME;
//    public static final CIKey RECEIVED_TIME_HISTORY = CIKeys.RECEIVED_TIME_HISTORY;
//    public static final CIKey REMOTE_ADDRESS = CIKeys.REMOTE_ADDRESS;
//    public static final CIKey REMOTE_CERT_EXPIRY = CIKeys.REMOTE_CERT_EXPIRY;
//    public static final CIKey REMOTE_FILE = CIKeys.REMOTE_FILE;
//    public static final CIKey REMOTE_HOST = CIKeys.REMOTE_HOST;
//    public static final CIKey START = CIKeys.START;
//    public static final CIKey STATUS = CIKeys.STATUS;
//    public static final CIKey STREAM_ID = CIKeys.STREAM_ID;
//    public static final CIKey STREAM_SIZE = CIKeys.STREAM_SIZE;
//    public static final CIKey SUBJECT = CIKeys.SUBJECT;
//    public static final CIKey TERMINAL = CIKeys.TERMINAL;
//    public static final CIKey TIME = CIKeys.TIME;
//    public static final CIKey TITLE = CIKeys.TITLE;
//    public static final CIKey TYPE = CIKeys.TYPE;
//    public static final CIKey UPLOAD_USERNAME = CIKeys.UPLOAD_USERNAME;
//    public static final CIKey UPLOAD_USER_ID = CIKeys.UPLOAD_USER_ID;
//    public static final CIKey UUID = CIKeys.UUID;
//    public static final CIKey VALUE = CIKeys.VALUE;
//    public static final CIKey VALUE_TYPE = CIKeys.VALUE_TYPE;
//
//    // Lower case keys
//    public static final CIKey ACCEPT = CIKeys.ACCEPT;
//    public static final CIKey CONNECTION = CIKeys.CONNECTION;
//    public static final CIKey EXPECT = CIKeys.EXPECT;
//
//    // kebab case keys
//    public static final CIKey CONTENT___ENCODING = CIKeys.CONTENT___ENCODING;
//    public static final CIKey CONTENT___LENGTH = CIKeys.CONTENT___LENGTH;
//    public static final CIKey TRANSFER___ENCODING = CIKeys.TRANSFER___ENCODING;
//    public static final CIKey USER___AGENT = CIKeys.USER___AGENT;
//    public static final CIKey X___FORWARDED___FOR = CIKeys.X___FORWARDED___FOR;
//
//    // Upper sentence case keys
//    public static final CIKey ANALYTIC__RULE = CIKeys.ANALYTIC__RULE;
//    public static final CIKey CREATE__TIME = CIKeys.CREATE__TIME;
//    public static final CIKey CREATE__TIME__MS = CIKeys.CREATE__TIME__MS;
//    public static final CIKey DOC__COUNT = CIKeys.DOC__COUNT;
//    public static final CIKey EFFECTIVE__TIME = CIKeys.EFFECTIVE__TIME;
//    public static final CIKey END__TIME = CIKeys.END__TIME;
//    public static final CIKey END__TIME__MS = CIKeys.END__TIME__MS;
//    public static final CIKey ERROR__COUNT = CIKeys.ERROR__COUNT;
//    public static final CIKey FATAL__ERROR__COUNT = CIKeys.FATAL__ERROR__COUNT;
//    public static final CIKey FILE__SIZE = CIKeys.FILE__SIZE;
//    public static final CIKey INDEX__NAME = CIKeys.INDEX__NAME;
//    public static final CIKey INFO__COUNT = CIKeys.INFO__COUNT;
//    public static final CIKey LAST__COMMIT = CIKeys.LAST__COMMIT;
//    public static final CIKey META__ID = CIKeys.META__ID;
//    public static final CIKey PARENT__CREATE__TIME = CIKeys.PARENT__CREATE__TIME;
//    public static final CIKey PARENT__FEED = CIKeys.PARENT__FEED;
//    public static final CIKey PARENT__ID = CIKeys.PARENT__ID;
//    public static final CIKey PARENT__STATUS = CIKeys.PARENT__STATUS;
//    public static final CIKey PIPELINE__NAME = CIKeys.PIPELINE__NAME;
//    public static final CIKey PROCESSOR__DELETED = CIKeys.PROCESSOR__DELETED;
//    public static final CIKey PROCESSOR__ENABLED = CIKeys.PROCESSOR__ENABLED;
//    public static final CIKey PROCESSOR__FILTER__DELETED = CIKeys.PROCESSOR__FILTER__DELETED;
//    public static final CIKey PROCESSOR__FILTER__ENABLED = CIKeys.PROCESSOR__FILTER__ENABLED;
//    public static final CIKey PROCESSOR__FILTER__ID = CIKeys.PROCESSOR__FILTER__ID;
//    public static final CIKey PROCESSOR__FILTER__LAST__POLL__MS = CIKeys.PROCESSOR__FILTER__LAST__POLL__MS;
//    public static final CIKey PROCESSOR__FILTER__PRIORITY = CIKeys.PROCESSOR__FILTER__PRIORITY;
//    public static final CIKey PROCESSOR__FILTER__UUID = CIKeys.PROCESSOR__FILTER__UUID;
//    public static final CIKey PROCESSOR__ID = CIKeys.PROCESSOR__ID;
//    public static final CIKey PROCESSOR__PIPELINE = CIKeys.PROCESSOR__PIPELINE;
//    public static final CIKey PROCESSOR__TASK__ID = CIKeys.PROCESSOR__TASK__ID;
//    public static final CIKey PROCESSOR__TYPE = CIKeys.PROCESSOR__TYPE;
//    public static final CIKey PROCESSOR__UUID = CIKeys.PROCESSOR__UUID;
//    public static final CIKey RAW__SIZE = CIKeys.RAW__SIZE;
//    public static final CIKey READ__COUNT = CIKeys.READ__COUNT;
//    public static final CIKey START__TIME = CIKeys.START__TIME;
//    public static final CIKey START__TIME__MS = CIKeys.START__TIME__MS;
//    public static final CIKey STATUS__TIME = CIKeys.STATUS__TIME;
//    public static final CIKey STATUS__TIME__MS = CIKeys.STATUS__TIME__MS;
//    public static final CIKey TASK__ID = CIKeys.TASK__ID;
//    public static final CIKey VOLUME__GROUP = CIKeys.VOLUME__GROUP;
//    public static final CIKey VOLUME__PATH = CIKeys.VOLUME__PATH;
//    public static final CIKey WARNING__COUNT = CIKeys.WARNING__COUNT;
//    public static final CIKey WRITE__COUNT = CIKeys.WRITE__COUNT;
//
//    // Reference Data fields
//    public static final CIKey FEED__NAME = CIKeys.FEED__NAME;
//    public static final CIKey LAST__ACCESSED__TIME = CIKeys.LAST__ACCESSED__TIME;
//    public static final CIKey MAP__NAME = CIKeys.MAP__NAME;
//    public static final CIKey PART__NUMBER = CIKeys.PART__NUMBER;
//    public static final CIKey PIPELINE__VERSION = CIKeys.PIPELINE__VERSION;
//    public static final CIKey PROCESSING__STATE = CIKeys.PROCESSING__STATE;
//    public static final CIKey REFERENCE__LOADER__PIPELINE = CIKeys.REFERENCE__LOADER__PIPELINE;
//    public static final CIKey STREAM__ID = CIKeys.STREAM__ID;
//    public static final CIKey VALUE__REFERENCE__COUNT = CIKeys.VALUE__REFERENCE__COUNT;
//
//    // Annotations keys
//    public static final CIKey ANNO_ASSIGNED_TO = CIKeys.ANNO_ASSIGNED_TO;
//    public static final CIKey ANNO_COMMENT = CIKeys.ANNO_COMMENT;
//    public static final CIKey ANNO_CREATED_BY = CIKeys.ANNO_CREATED_BY;
//    public static final CIKey ANNO_CREATED_ON = CIKeys.ANNO_CREATED_ON;
//    public static final CIKey ANNO_HISTORY = CIKeys.ANNO_HISTORY;
//    public static final CIKey ANNO_ID = CIKeys.ANNO_ID;
//    public static final CIKey ANNO_STATUS = CIKeys.ANNO_STATUS;
//    public static final CIKey ANNO_SUBJECT = CIKeys.ANNO_SUBJECT;
//    public static final CIKey ANNO_TITLE = CIKeys.ANNO_TITLE;
//    public static final CIKey ANNO_UPDATED_BY = CIKeys.ANNO_UPDATED_BY;
//    public static final CIKey ANNO_UPDATED_ON = CIKeys.ANNO_UPDATED_ON;
//
//    public static final CIKey UNDERSCORE_EVENT_ID = CIKeys.UNDERSCORE_EVENT_ID;
//    public static final CIKey UNDERSCORE_STREAM_ID = CIKeys.UNDERSCORE_STREAM_ID;
//    public static final CIKey UNDERSCORE_TIME = CIKeys.UNDERSCORE_TIME;

    @JsonValue // No need to serialise the CIKey wrapper, just the key
    private final String key;

    @JsonIgnore
    private final transient String lowerKey;

    @JsonCreator
    private CIKey(final String key) {
        this.key = key;
        this.lowerKey = toLowerCase(key);
    }

    /**
     * key and lowerKey must be equal ignoring case.
     *
     * @param key      The key
     * @param lowerKey The key converted to lower-case
     */
    CIKey(final String key, final String lowerKey) {
        this.key = key;
        this.lowerKey = lowerKey;
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo".
     * If key is all lower case then user {@link CIKey#ofLowerCase(String)}.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
     * <p>
     * The returned {@link CIKey} will wrap key with no change of case.
     * </p>
     */
    public static CIKey of(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> new CIKey(key));
        }
    }

    /**
     * Equivalent to calling {@link CIKey#of(String)} with a trimmed key.
     */
    public static CIKey trimmed(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else {
            final String trimmed = key.trim();
            return CIKey.of(trimmed);
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo",
     * when you already know the lower-case form of the key.
     * If key is all lower case then user {@link CIKey#ofLowerCase(String)}.
     * If key is a common key this method will return an existing {@link CIKey} instance
     * else it will create a new instance.
     */
    public static CIKey of(final String key, final String lowerKey) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> new CIKey(key, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for key, providing a map of known {@link CIKey}s keyed
     * on their key value. Allows callers to hold their own set of known {@link CIKey}s.
     */
    public static CIKey of(final String key, final Map<String, CIKey> knownKeys) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            CIKey ciKey = null;
            if (knownKeys != null) {
                ciKey = knownKeys.get(key);
            }
            if (ciKey == null) {
                ciKey = CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key);
                if (ciKey == null) {
                    ciKey = new CIKey(key);
                }
            }
            return ciKey;
        }
    }

    /**
     * Create a {@link CIKey} for an all lower case key, e.g. "foo".
     * This is a minor optimisation to avoid a call to toLowerCase as the
     * key is already in lower-case.
     */
    public static CIKey ofLowerCase(final String lowerKey) {
        if (lowerKey == null) {
            return NULL_STRING;
        } else if (lowerKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(lowerKey),
                    () -> new CIKey(lowerKey, lowerKey));
        }
    }

    /**
     * Create a {@link CIKey} for a key that is known NOT to be in {@link CIKey}s list
     * of common keys and is a key that will not be added to the list of common keys in future.
     * This is a minor optimisation.
     */
    public static CIKey ofDynamicKey(final String dynamicKey) {
        if (dynamicKey == null) {
            return NULL_STRING;
        } else if (dynamicKey.isEmpty()) {
            return EMPTY_STRING;
        } else {
            return new CIKey(dynamicKey);
        }
    }

    /**
     * Create a {@link CIKey} for an upper or mixed case key, e.g. "FOO", or "Foo",
     * that will be held as a static variable. This has the additional cost of
     * interning the lower-case form of the key. Only use this for static {@link CIKey}
     * instances as
     */
    public static CIKey ofStaticKey(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            // See if we have a common key that matches exactly with the one requested.
            // Case-sensitive here because CIKey should wrap the exact case passed in.
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> CIKeys.commonKey(key));
        }
    }

    /**
     * If ciKey matches a common {@link CIKey} (ignoring case) then return the common
     * {@link CIKey} else return ciKey. Use this if you don't care about the case of the
     * wrapped string, e.g. if key is 'FOO', you could get back a {@link CIKey} that wraps
     * 'foo', 'FOO', 'Foo', etc.
     */
    public static CIKey ofIgnoringCase(final String key) {
        if (key == null) {
            return NULL_STRING;
        } else if (key.isEmpty()) {
            return EMPTY_STRING;
        } else {
            final String lowerKey = toLowerCase(key);
            return GwtNullSafe.requireNonNullElseGet(
                    CIKeys.LOWER_KEY_TO_COMMON_CIKEY_MAP.get(key),
                    () -> CIKey.ofLowerCase(lowerKey));
        }
    }


    /**
     * @return The wrapped string in its original case.
     */
    @JsonIgnore
    public String get() {
        return key;
    }

    /**
     * Here for JSON (de-)ser.
     */
    private String getKey() {
        return key;
    }

    @JsonIgnore
    public String getAsLowerCase() {
        return lowerKey;
    }

    public boolean equalsIgnoreCase(final String str) {
        return CIKey.equalsIgnoreCase(this, str);
    }

    /**
     * Standard equals method for comparing two {@link CIKey} instances, comparing the
     * lowerKey of each.
     */
    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CIKey that = (CIKey) object;
        return Objects.equals(lowerKey, that.lowerKey);
    }

    @Override
    public int hashCode() {
        // String lazily caches its hashcode so no need for us to do it too
        return lowerKey != null
                ? lowerKey.hashCode()
                : 0;
    }

    @Override
    public String toString() {
        return key;
    }

    @Override
    public int compareTo(final CIKey o) {
        Objects.requireNonNull(o);
        return COMPARATOR.compare(this, o);
    }

    /**
     * Returns true if the string this {@link CIKey} wraps contains subString
     * ignoring case.
     * If subString is all lower case, use {@link CIKey#containsLowerCase(String)} instead.
     */
    public boolean containsIgnoreCase(final String subString) {
        Objects.requireNonNull(subString);
        if (lowerKey == null) {
            return false;
        }
        return lowerKey.contains(toLowerCase(subString));
    }

    /**
     * Returns true if the string this {@link CIKey} wraps contains (ignoring case) lowerSubString.
     * {@code lowerSubString} MUST be all lower case.
     * <p>
     * This method is a slight optimisation to avoid having to lower-case the input if it
     * is know to already be lower-case.
     * </p>
     * If lowerSubString is mixed or upper case, use {@link CIKey#containsIgnoreCase(String)} instead.
     */
    public boolean containsLowerCase(final String lowerSubString) {
        Objects.requireNonNull(lowerSubString);
        if (lowerKey == null) {
            return false;
        }
        return lowerKey.contains(toLowerCase(lowerSubString));
    }

    /**
     * @param keys
     * @return True if this key matches one of keys (ignoring case)
     */
    public boolean in(final Collection<String> keys) {
        if (GwtNullSafe.hasItems(keys)) {
            return keys.stream()
                    .anyMatch(aKey ->
                            CIKey.equalsIgnoreCase(this, aKey));
        } else {
            return false;
        }
    }

    /**
     * @return True if ciKey is null or wraps a null string
     */
    public static boolean isNull(final CIKey ciKey) {
        return ciKey == null || ciKey.key == null;
    }

    public boolean isEmpty(final CIKey ciKey) {
        return ciKey.key == null || ciKey.key.isEmpty();
    }

    public boolean isEmpty() {
        return key == null || key.isEmpty();
    }

    /**
     * Create a case-insensitive keyed {@link Entry} from a {@link String} key and value of type T.
     */
    public static <V> Entry<CIKey, V> entry(final String key, final V value) {
        return Map.entry(CIKey.of(key), value);
    }

    /**
     * Create a case-insensitive keyed {@link Entry} from a simple {@link String} keyed {@link Entry}.
     */
    public static <T> Entry<CIKey, T> entry(final Entry<String, T> entry) {
        if (entry == null) {
            return null;
        } else {
            return Map.entry(CIKey.of(entry.getKey()), entry.getValue());
        }
    }

    public static List<CIKey> listOf(final String... keys) {
        return GwtNullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toList());
    }

    public static Set<CIKey> setOf(final String... keys) {
        return GwtNullSafe.stream(keys)
                .map(CIKey::of)
                .collect(Collectors.toSet());
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1) {
        return Map.of(CIKey.of(k1), v1);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1, String k2, V v2) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6,
                                          String k7, V v7) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6,
                CIKey.of(k7), v7);
    }

    /**
     * Create a {@link CIKey} keyed map
     */
    public static <V> Map<CIKey, V> mapOf(String k1, V v1,
                                          String k2, V v2,
                                          String k3, V v3,
                                          String k4, V v4,
                                          String k5, V v5,
                                          String k6, V v6,
                                          String k7, V v7,
                                          String k8, V v8) {
        return Map.of(
                CIKey.of(k1), v1,
                CIKey.of(k2), v2,
                CIKey.of(k3), v3,
                CIKey.of(k4), v4,
                CIKey.of(k5), v5,
                CIKey.of(k6), v6,
                CIKey.of(k7), v7,
                CIKey.of(k8), v8);
    }

    /**
     * Convert an array of {@link String} keyed entries into a {@link CIKey} keyed map.
     */
    @SafeVarargs
    public static <V> Map<CIKey, V> mapOfEntries(final Entry<String, ? extends V>... entries) {
        return GwtNullSafe.stream(entries)
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    /**
     * Convert a {@link String} keyed map into a {@link CIKey} keyed map.
     * Accepts nulls and never returns a null.
     */
    public static <V> Map<CIKey, V> mapOf(final Map<String, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry ->
                                CIKey.of(entry.getKey()),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToStringMap(final Map<CIKey, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().get(),
                        Entry::getValue));
    }

    public static <V> Map<String, V> convertToLowerCaseStringMap(final Map<CIKey, ? extends V> map) {
        return GwtNullSafe.map(map)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().getAsLowerCase(),
                        Entry::getValue));
    }

    public static <V> V put(final Map<CIKey, V> map,
                            final String key,
                            final V value) {
        return map.put(CIKey.of(key), value);
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final String str1, final String str2) {
        if (str1 == null && str2 == null) {
            return true;
        } else {
            return str1 != null && str1.equalsIgnoreCase(str2);
        }
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final CIKey ciKey, final String str) {
        return equalsIgnoreCase(str, ciKey);
    }

    /**
     * True if str is equal to the string wrapped by ciKey, ignoring case.
     */
    public static boolean equalsIgnoreCase(final String str, final CIKey ciKey) {
        final String lowerKey = ciKey.lowerKey;
        if (lowerKey == null && str == null) {
            return true;
        } else {
            return lowerKey != null && lowerKey.equalsIgnoreCase(str);
        }
    }

    /**
     * Method so we have a consistent way of doing it, in the unlikely event it changes.
     */
    static String toLowerCase(final String str) {
        return GwtNullSafe.get(str, s -> s.toLowerCase(Locale.ENGLISH));
    }
}

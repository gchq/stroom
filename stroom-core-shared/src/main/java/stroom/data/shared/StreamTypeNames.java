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

package stroom.data.shared;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A set of constants for stream type names used in stroom. This is NOT an exhaustive list
 * of stream type names. Addition stream types can be configured using
 * MetaServiceConfig#getMetaTypes
 * Some of the names in {@link StreamTypeNames} are referenced in code and logic is based around them.
 */
public class StreamTypeNames {

    // TODO 06/01/2022 AT: The ultimate aim is for stream types to be fully configurable and not baked in
    //  however we have a lot of code that is conditional on certain stream type names so we have to make do
    //  for now.

    // TODO 06/01/2022 AT: This ought to be an enum but not going to take the risk of a refactor
    //  when we are about to deploy v7.0 and the resulting merge pain. A job for master.

    // ********************************************************************************
    //   IMPORTANT:
    //
    // If you add/change this list of names consider changing the required values
    // validation for stroom.meta.impl.MetaServiceConfig#getMetaTypes and
    // stroom.proxy.app.handler.ProxyRequestConfig#getMetaTypes
    // Type names in here that are referenced in the code should be required in the config.
    // ********************************************************************************

    /**
     * Saved raw version for the archive.
     */
    public static final String RAW_EVENTS = "Raw Events";
    /**
     * Saved raw version for the archive.
     */
    public static final String RAW_REFERENCE = "Raw Reference";
    /**
     * Processed events Data files.
     */
    public static final String EVENTS = "Events";
    /**
     * Processed reference Data files.
     */
    public static final String REFERENCE = "Reference";
    /**
     * Processed Data files conforming to the Records XMLSchema.
     */
    public static final String RECORDS = "Records";
    /**
     * Meta meta data
     */
    public static final String META = "Meta Data";
    /**
     * Processed events Data files.
     */
    public static final String ERROR = "Error";
    /**
     * Context file for use with an events file.
     */
    public static final String CONTEXT = "Context";
    /**
     * Processed test events data files
     */
    public static final String TEST_EVENTS = "Test Events";
    /**
     * Processed test reference data files
     */
    public static final String TEST_REFERENCE = "Test Reference";
    /**
     * Processed detections
     */
    public static final String DETECTIONS = "Detections";

    /**
     * Must NOT be used for stream type name validation or getting a full list
     * of stream types. Use MetaServiceConfig#getMetaTypes or
     * ProxyRequestConfig#getMetaTypes to get a list of
     * all configured stream types.
     */
    // GWT so can't use Set.of() :-(
    public static final Set<String> ALL_HARD_CODED_STREAM_TYPE_NAMES = new HashSet<>(Arrays.asList(
            CONTEXT,
            DETECTIONS,
            ERROR,
            EVENTS,
            META,
            RAW_EVENTS,
            RAW_REFERENCE,
            RECORDS,
            REFERENCE,
            TEST_EVENTS,
            TEST_REFERENCE));

    /**
     * Must NOT be used for stream type name validation or getting a full list
     * of raw stream types. Use MetaServiceConfig#getMetaTypes to get a list of
     * all configured raw stream types.
     * Fine for use in mocks.
     */
    // GWT so can't use Set.of() :-(
    // Set content must match the @IsSupersetOf anno on MetaServiceConfig#getRawMetaTypes
    public static final Set<String> ALL_HARD_CODED_RAW_STREAM_TYPE_NAMES = new HashSet<>(Arrays.asList(
            RAW_EVENTS,
            RAW_REFERENCE));
}

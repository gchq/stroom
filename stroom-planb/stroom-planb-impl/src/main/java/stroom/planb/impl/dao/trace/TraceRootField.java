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

package stroom.planb.impl.dao.trace;

/**
 * Sort-field identifier constants for the traces list UI, matching the
 * {@code .withSorting(name)} calls in {@code TracesListPresenter}.
 *
 * <p>These values are carried by {@link stroom.util.shared.CriteriaFieldSort#getId()}
 * and used by {@link TraceDb#findTraces} to select the appropriate LMDB secondary
 * index for an efficient sorted range scan.
 */
public final class TraceRootField {

    /** Root-span operation name column ({@code trace-roots-operation} index). */
    public static final String OPERATION   = "Operation";
    /** Trace ID (hex string) column ({@code trace-roots} primary index). */
    public static final String TRACE_ID    = "Trace Id";
    /** Trace start time column ({@code trace-roots-start-time} index). */
    public static final String TRACE_START = "Trace Start";
    /** Root operation's own duration ({@code rootEndTime - startTime}, {@code trace-roots-duration} index). */
    public static final String DURATION    = "Root Duration";
    /** Whole-trace span — start to the last span's end ({@code endTime - startTime}. */
    public static final String TOTAL_DURATION = "Trace Duration";
    /** Service-count column ({@code trace-roots-services} index). */
    public static final String SERVICES    = "Services";
    /** Span depth column ({@code trace-roots-depth} index). */
    public static final String DEPTH       = "Depth";
    /** Total-spans column ({@code trace-roots-total-spans} index). */
    public static final String TOTAL_SPANS = "Total Spans";

    private TraceRootField() {
    }
}

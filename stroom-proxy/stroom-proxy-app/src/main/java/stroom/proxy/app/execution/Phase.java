/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.execution;

/**
 * Where a piece of background work sits in the flow of data. Declared in data-flow order: stop
 * runs the phases in this order, so a producer has stopped before its consumer is asked to, and
 * start runs them in reverse, so a consumer is draining before its producer feeds it.
 */
public enum Phase {
    /** Sources: the directory scanner, the SQS connector, the event store's forwarder. */
    INGRESS,
    /** The stages, in pipeline order. Receive is Dropwizard's HTTP threads, not the registry's. */
    SPLIT_ZIP,
    /** The aggregate stage's claimers, then its merge workers. */
    AGGREGATE,
    /** The forward stage's loop, each destination's loop when fanning out, and their liveness watches. */
    FORWARD,
    /** Sweeps, rolls and refreshes; anything a stage may still call into while it stops. */
    HOUSEKEEPING
}

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

package stroom.proxy.app.pipeline.stress;

import java.io.IOException;

/**
 * Thrown by the stress harness's fault injectors, never by production code.
 * <p>
 * It is a distinct type so that a scenario can tell an injected fault apart from
 * a genuine failure. Without that distinction a harness that is silently broken -
 * one that throws {@link IOException} from its own plumbing - looks exactly like
 * a successful fault-injection run.
 * </p>
 */
public class InjectedFaultException extends IOException {

    private final FaultPoint faultPoint;

    public InjectedFaultException(final FaultPoint faultPoint) {
        super("Injected fault at " + faultPoint);
        this.faultPoint = faultPoint;
    }

    public FaultPoint getFaultPoint() {
        return faultPoint;
    }
}

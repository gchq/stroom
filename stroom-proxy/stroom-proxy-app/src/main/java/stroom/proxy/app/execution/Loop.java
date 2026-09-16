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
 * A registered loop, as seen by the component that registered it and by health.
 */
public interface Loop {

    String getName();

    Phase getPhase();

    /**
     * Let running tasks finish and start no new ones until {@link #resume()}.
     */
    void pause();

    void resume();

    boolean isPaused();

    /**
     * Threads currently inside the loop. Fewer than {@link #configuredThreads()} once started is a
     * defect: a loop thread never dies on purpose.
     */
    int liveThreads();

    int configuredThreads();
}

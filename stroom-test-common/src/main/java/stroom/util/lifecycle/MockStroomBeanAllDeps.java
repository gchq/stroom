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

package stroom.util.lifecycle;


import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class MockStroomBeanAllDeps extends MockStroomBeanLifeCycleBean {
    private final MockStroomBeanNoDeps stroomBeanNoDeps;
    private final MockStroomBeanSomeDeps stroomBeanSomeDeps;

    @Inject
    MockStroomBeanAllDeps(final MockStroomBeanNoDeps stroomBeanNoDeps,
                          final MockStroomBeanSomeDeps stroomBeanSomeDeps) {
        this.stroomBeanNoDeps = stroomBeanNoDeps;
        this.stroomBeanSomeDeps = stroomBeanSomeDeps;
    }

    @Override
    @StroomStartup
    public void start() {
        assertThat(stroomBeanNoDeps.isRunning()).isTrue();
        assertThat(stroomBeanSomeDeps.isRunning()).isTrue();

        super.start();
    }

    @Override
    @StroomShutdown(priority = 100)
    public void stop() {
        super.stop();
    }
}

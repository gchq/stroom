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

package stroom.lifecycle;

import stroom.util.shared.VoidResult;
import stroom.task.api.ServerTask;

public class LifecycleTask extends ServerTask<VoidResult> {
    private StroomBeanMethodExecutable executable;
    private StroomBeanFunction function;

    public LifecycleTask(final StroomBeanMethodExecutable executable) {
        this.executable = executable;
    }

    public LifecycleTask(final StroomBeanFunction function) {
        this.function = function;
    }

    // TODO: clean up gh-1063
    public StroomBeanMethodExecutable getExecutable() {
        return executable;
    }

    public StroomBeanFunction getFunction() {
        return function;
    }

    @Override
    public String getTaskName() {
        if(executable != null){
            return executable.toString();
        }else {
            return function.toString();
        }
    }

}

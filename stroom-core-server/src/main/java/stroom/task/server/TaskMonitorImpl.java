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

package stroom.task.server;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.shared.Monitor;
import stroom.util.shared.TerminateHandler;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

@Component("taskMonitor")
@Scope(value = StroomScope.TASK)
public final class TaskMonitorImpl implements TaskMonitor {
    private static final long serialVersionUID = 482019617293759705L;

    private volatile Monitor monitor;

    public void setMonitor(final Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public String getName() {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            return monitor.getName();
        }
        return null;
    }

    @Override
    public void setName(final String name) {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            monitor.setName(name);
        }
    }

    @Override
    public String getInfo() {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            return monitor.getInfo();
        }
        return null;
    }

    @Override
    public void info(final Object... args) {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            monitor.info(args);
        }
    }

    @Override
    public boolean isTerminated() {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            return monitor.isTerminated();
        }
        return false;
    }

    @Override
    public void terminate() {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            monitor.terminate();
        }
    }

    @Override
    public void addTerminateHandler(final TerminateHandler handler) {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            monitor.addTerminateHandler(handler);
        }
    }

    @Override
    public Monitor getParent() {
        final Monitor monitor = this.monitor;
        if (monitor != null) {
            return monitor.getParent();
        }
        return null;
    }
}

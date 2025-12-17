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

package stroom.util.guice;

import stroom.util.sysinfo.HasSystemInfo;

import com.google.inject.Binder;
import com.google.inject.multibindings.Multibinder;

public class HasSystemInfoBinder {

    private final Multibinder<HasSystemInfo> multibinder;

    private HasSystemInfoBinder(final Binder binder) {
        multibinder = Multibinder.newSetBinder(binder, HasSystemInfo.class);
    }

    public static HasSystemInfoBinder create(final Binder binder) {
        return new HasSystemInfoBinder(binder);
    }

    public <H extends HasSystemInfo> HasSystemInfoBinder bind(final Class<H> hasSystemInfoClass) {
        multibinder.addBinding().to(hasSystemInfoClass);
        return this;
    }
}

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

package stroom.activity.impl;

import stroom.activity.api.ActivityService;
import stroom.activity.api.CurrentActivity;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class ActivityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ActivityService.class).to(ActivityServiceImpl.class);
        bind(CurrentActivity.class).to(CurrentActivityImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ActivityResourceImpl.class);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}

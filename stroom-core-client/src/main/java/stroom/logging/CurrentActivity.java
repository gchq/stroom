/*
 * Copyright 2018 Crown Copyright
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

package stroom.logging;

import event.logging.Data;
import org.springframework.stereotype.Component;
import stroom.activity.shared.Activity;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class CurrentActivity {
    private final Provider<CurrentActivitySession> currentActivitySessionProvider;

    @Inject
    public CurrentActivity(final Provider<CurrentActivitySession> currentActivitySessionProvider) {
        this.currentActivitySessionProvider = currentActivitySessionProvider;
    }

    public Activity getActivity() {
        return currentActivitySessionProvider.get().getActivity();
    }

    public void setActivity(final Activity activity) {
        currentActivitySessionProvider.get().setActivity(activity);
    }

    void decorate(final Supplier<List<Data>> supplier) {
        getData().ifPresent(data -> supplier.get().add(data));
    }

    @SuppressWarnings("unchecked")
    void decorate(final Object object) {
        getData().ifPresent(data -> {
            try {
                final Method method = object.getClass().getMethod("getData");
                final Object ret = method.invoke(object);
                if (ret != null) {
                    final List<Data> list = (List) ret;
                    list.add(data);
                }

            } catch (final NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                // Ignore.
            }
        });
    }

    private Optional<Data> getData() {
        final Activity activity = getActivity();
        if (activity == null) {
            return Optional.empty();
        }

        final Data data = new Data();
        data.setName("activity");

        activity.getDetails().getNames().forEach(name -> {
            final String value = activity.getDetails().getProperties().get(name);
            if (value != null) {
                final Data d = new Data();
                d.setName(name);
                d.setValue(value);
                data.getData().add(d);
            }
        });

        return Optional.of(data);
    }
}


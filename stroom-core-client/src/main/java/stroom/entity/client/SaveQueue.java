/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity.client;

import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityServiceSaveAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SaveQueue<E extends BaseEntity> {
    private final ClientDispatchAsync dispatcher;
    private final Map<E, List<SaveTask<E>>> map = new HashMap<>();

    public SaveQueue(final ClientDispatchAsync dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void save(final SaveTask<E> task) {
        List<SaveTask<E>> queue = map.get(task.getEntity());
        if (queue == null) {
            queue = new ArrayList<>();
            map.put(task.getEntity(), queue);
            queue.add(task);
            saveAsync(task.getEntity());
        } else {
            queue.add(task);
        }
    }

    private void saveAsync(final E entity) {
        // Update the entity with all queued updates.
        final List<SaveTask<E>> queue = new ArrayList<>(map.get(entity));
        for (final SaveTask<E> task : queue) {
            task.setValue(entity);
        }

        dispatcher.exec(new EntityServiceSaveAction<>(entity))
                .onSuccess(result -> {
                    if (result != null) {
                        // Get all of the tasks that are queued for this
                        // entity including ones that may have been added
                        // since we saved the last list of tasks.
                        final List<SaveTask<E>> allTasks = map.get(result);

                        // Set result on all tasks that we saved and remove
                        // them from the complete task list.
                        for (final SaveTask<E> task : queue) {
                            task.setEntity(result);
                            allTasks.remove(task);
                        }

                        // If there are any tasks left in the complete task
                        // list then save them using the latest entity.
                        if (allTasks.size() > 0) {
                            saveAsync(result);
                        } else {
                            map.remove(result);
                        }

                    } else {
                        map.remove(entity);
                    }

                    onComplete();
                })
                .onFailure(caught -> map.remove(entity));
    }

    public void onComplete() {
    }
}

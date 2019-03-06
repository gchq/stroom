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
import stroom.docref.SharedObject;
import stroom.task.shared.Action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionQueue<E extends SharedObject> {
    private final ClientDispatchAsync dispatcher;
    private final Map<E, List<Action<E>>> map = new HashMap<>();

    public ActionQueue(final ClientDispatchAsync dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void dispatch(final E object, final Action<E> task) {
        List<Action<E>> queue = map.get(object);
        if (queue == null) {
            map.computeIfAbsent(object, k -> new ArrayList<>()).add(task);
            dispatchAsync(object);
        } else {
            queue.add(task);
        }
    }

    private void dispatchAsync(final E object) {
        // Update the object with all queued updates.
        final List<Action<E>> queue = map.remove(object);
        if (queue != null && queue.size() > 0) {
            final Action<E> action = queue.get(queue.size() - 1);
            dispatcher.exec(action)
                    .onSuccess(result -> {
                        if (result != null) {
                            // Get all of the tasks that are queued for this
                            // object including ones that may have been added
                            // since we saved the last list of tasks.
                            final List<Action<E>> allTasks = map.get(result);

                            // If there are any tasks left in the complete task
                            // list then save them using the latest object.
                            if (allTasks != null && allTasks.size() > 0) {
                                dispatchAsync(result);
                            } else {
                                complete(object);
                            }

                        } else {
                            complete(object);
                        }
                    })
                    .onFailure(caught -> complete(object));
        }
    }

    private void complete(final E object) {
        map.remove(object);
        onComplete();
    }

    public void onComplete() {
    }
}

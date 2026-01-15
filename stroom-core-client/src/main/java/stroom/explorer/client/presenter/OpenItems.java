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

package stroom.explorer.client.presenter;

import java.util.HashSet;
import java.util.Set;

public class OpenItems<T> {

    private final Set<T> openItems = new HashSet<>();
    private Set<T> temporaryOpenItems = new HashSet<>();

    public void open(final T item) {
        openItems.add(item);
    }

    public void close(final T item) {
        openItems.remove(item);
        temporaryOpenItems.remove(item);
    }

    public void clear() {
        openItems.clear();
        temporaryOpenItems.clear();
    }

    public boolean isOpen(final T item) {
        return openItems.contains(item) || temporaryOpenItems.contains(item);
    }

    public boolean toggleOpenState(final T item) {
        if (isOpen(item)) {
            close(item);
            return false;
        } else {
            open(item);
            return true;
        }
    }

    Set<T> getAllOpenItems() {
        // Ensure that we always get a new set returned so that changes to the open items after this set is returned are
        // not reflected in the returned set.
        final Set<T> combined = new HashSet<>();
        combined.addAll(openItems);
        combined.addAll(temporaryOpenItems);
        return combined;
    }

    Set<T> getOpenItems() {
        return new HashSet<>(this.openItems);
    }

    Set<T> getTemporaryOpenItems() {
        return new HashSet<>(this.temporaryOpenItems);
    }

    void setTemporaryOpenItems(final Set<T> temporaryOpenItems) {
        this.temporaryOpenItems = temporaryOpenItems;
    }
}

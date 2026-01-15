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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import org.xml.sax.SAXException;

public abstract class StoreNode extends Node {

    private final int[] allReferencedGroups;
    private final int[] localReferencedGroups;
    private final int[] remoteReferencedGroups;

    private final Store[] localStores;
    private final Store[] remoteStores;

    public StoreNode(final VarMap varMap, final StoreFactory factory) {
        super(varMap, factory);

        if (factory.getAllReferencedGroups().getArr() != null) {
            allReferencedGroups = factory.getAllReferencedGroups().getArr();
        } else {
            allReferencedGroups = null;
        }

        final UniqueInt localGroups = factory.getLocalReferencedGroups();
        if (localGroups.getArr() != null) {
            localReferencedGroups = localGroups.getArr();
            final int maxLocalGroup = localGroups.getMax();
            localStores = new Store[maxLocalGroup + 1];
            for (final int referencedGroup : localReferencedGroups) {
                localStores[referencedGroup] = new LocalStore();
            }
        } else {
            localReferencedGroups = null;
            localStores = null;
        }

        final UniqueInt remoteGroups = factory.getRemoteReferencedGroups();
        if (remoteGroups.getArr() != null) {
            remoteReferencedGroups = remoteGroups.getArr();
            final int maxRemoteGroup = remoteGroups.getMax();
            remoteStores = new Store[maxRemoteGroup + 1];
            for (final int referencedGroup : remoteReferencedGroups) {
                remoteStores[referencedGroup] = new RemoteStore(factory.getDebugId());
            }
        } else {
            remoteReferencedGroups = null;
            remoteStores = null;
        }
    }

    public void storeValue(final int groupNo, final int parentMatchCount, final Buffer value) throws SAXException {
        // Store locally.
        if (localStores != null && localStores.length > groupNo) {
            final Store store = localStores[groupNo];
            if (store != null) {
                store.set(parentMatchCount, value);
            }
        }

        // Store remotely.
        if (remoteStores != null && remoteStores.length > groupNo) {
            final Store store = remoteStores[groupNo];
            if (store != null) {
                store.set(parentMatchCount, value);
            }
        }
    }

    public void clearStores() {
        if (localStores != null) {
            for (final int group : localReferencedGroups) {
                localStores[group].clear();
            }
        }
        if (remoteStores != null) {
            for (final int group : remoteReferencedGroups) {
                remoteStores[group].clear();
            }
        }
    }

    public int[] getAllReferencedGroups() {
        return allReferencedGroups;
    }

    public Store getStore(final int group, final boolean local) {
        if (local) {
            return localStores[group];
        }

        return remoteStores[group];
    }

    @Override
    public boolean isExpression() {
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        clearStores();
    }
}

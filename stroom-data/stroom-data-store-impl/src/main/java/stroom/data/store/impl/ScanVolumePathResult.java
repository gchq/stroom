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

package stroom.data.store.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ScanVolumePathResult implements Serializable {

    private static final long serialVersionUID = -5119181751996452930L;

    public long fileCount = 0;
    public long tooNewToDeleteCount = 0;
    public List<String> childDirectoryList = new ArrayList<>();
    private final List<String> deleteList = new ArrayList<>();

    /**
     * @return the fileCount
     */
    public long getFileCount() {
        return fileCount;
    }

    public void setFileCount(final long fileCount) {
        this.fileCount = fileCount;
    }

    public List<String> getDeleteList() {
        return deleteList;
    }

    /**
     * @return the childDirectoryList
     */
    public List<String> getChildDirectoryList() {
        return childDirectoryList;
    }

    public void addChildDirectory(final String path) {
        childDirectoryList.add(path);
    }

    public void addDelete(final String file) {
        deleteList.add(file);
    }

    public long getTooNewToDeleteCount() {
        return tooNewToDeleteCount;
    }

    public void incrementTooNewToDeleteCount() {
        tooNewToDeleteCount++;
    }

}

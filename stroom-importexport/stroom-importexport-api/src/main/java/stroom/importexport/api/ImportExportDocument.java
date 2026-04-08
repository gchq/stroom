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

package stroom.importexport.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents data that is being imported or exported from a Stroom document.
 * An asset can be exported in two ways:
 * <ul>
 *     <li>Within a file with an extension such as .meta. This is an Ext
 *         asset.</li>
 *     <li>Within a file where the key forms a path to the asset,
 *         under the document itself. This is a Path asset.</li>
 * </ul>
 * Use the appropriate methods to add and get such assets from this object.
 * <p>
 *     <b>Note:</b> there are important differences between the two assets.
 * </p>
 * <p>
 *     Ext assets are largely handled automatically within the system.
 *     They will be persisted to the DB for the doc. The system assumes
 *     that their contents is UTF8 text and will add a newline to the
 *     end of the file, if that is missing.
 * </p>
 * <p>
 *     Path assets are largely ignored within the system. It is up to
 *     each Document to persist these to the DB. Their contents can
 *     be binary.
 * </p>
 */
public class ImportExportDocument {

    private final Map<String, ImportExportAsset> extAssets = new HashMap<>();

    private final List<ImportExportAsset> pathAssets = new ArrayList<>();

    /**
     * Adds an asset that should be represented by a file with the key as
     * an extension.
     * @param asset The asset to add.
     */
    public void addExtAsset(final ImportExportAsset asset) {
        extAssets.put(asset.getKey(), asset);
    }

    /**
     * @return The assets that should be keyed by extension, as an unmodifiable collection.
     */
    public Collection<ImportExportAsset> getExtAssets() {
        return Collections.unmodifiableCollection(extAssets.values());
    }

    /**
     * Determines whether an extension asset exists with the given key.
     * @param key The key to check.
     * @return true if the key can be found, false if not.
     */
    public boolean containsExtAssetWithKey(final String key) {
        return extAssets.containsKey(key);
    }

    /**
     * Returns the extension asset that matches the given key.
     * @param key The key to search for.
     * @return The extension asset that has the given key, or null if no such asset.
     */
    public ImportExportAsset getExtAsset(final String key) {
        return extAssets.get(key);
    }

    /**
     * Returns and removes the extension asset that matches the given key.
     * @param key The asset key to find and remove.
     * @return The extension asset that has the given key, or null if no such asset exists.
     */
    public ImportExportAsset removeExtAsset(final String key) {
        return extAssets.remove(key);
    }

    /**
     * Returns the byte[] data for the asset with the given key, or null
     * if nothing is found. Avoids the client doing lots of null handling.
     * @param key The key of the required asset.
     * @return The data, or null if nothing is found.
     * @throws IOException If there is a problem reading the data.
     */
    public byte[] getExtAssetData(final String key) throws IOException {
        final ImportExportAsset asset = getExtAsset(key);
        if (asset != null) {
            return asset.getInputData();
        }
        return null;
    }

    /**
     * Adds an asset where the key represents a path to the asset in the exported
     * file structure.
     * @param asset The asset to add.
     */
    public void addPathAsset(final ImportExportAsset asset) {
        pathAssets.add(asset);
    }

    /**
     * @return The assets that should be keyed by path, as an unmodifiable collection.
     */
    public Collection<ImportExportAsset> getPathAssets() {
        return Collections.unmodifiableList(pathAssets);
    }

    /**
     * Temporary method to convert extension assets to old data format during conversion.
     */
    public Map<String, byte[]> toDataMap() throws IOException {
        final Map<String, byte[]> dataMap = new HashMap<>();
        for (final ImportExportAsset asset : extAssets.values()) {
            dataMap.put(asset.getKey(), asset.getInputData());
        }

        return dataMap;
    }

    /**
     * Converts a dataMap (legacy format) into the new ImportExportDocument.
     * @param data The data to convert. Can be null.
     * @return An ImportExportDocument.
     */
    public static ImportExportDocument fromDataMap(final Map<String, byte[]> data) {
        final ImportExportDocument importExportDocument = new ImportExportDocument();

        if (data != null) {
            for (final Map.Entry<String, byte[]> entry : data.entrySet()) {
                final ImportExportAsset asset = new ByteArrayImportExportAsset(entry.getKey(), entry.getValue());
                importExportDocument.addExtAsset(asset);
            }
        }

        return importExportDocument;
    }

}

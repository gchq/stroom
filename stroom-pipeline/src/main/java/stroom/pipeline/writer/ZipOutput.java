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

package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ZipOutput implements Output {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZipOutput.class);

    public static final String DATA_EXTENSION = ".dat";
    public static final String META_EXTENSION = ".meta";

    private final MetaDataHolder metaDataHolder;
    private final AttributeMap attributeMap;
    private ZipArchiveEntry currentZipEntry;
    private long count;

    private final ZipArchiveOutputStream zipOutputStream;
    private final ByteCountOutputStream outputStream;

    /**
     * Use the attributes from the metaDataHolder
     */
    public ZipOutput(final MetaDataHolder metaDataHolder,
                     final OutputStream innerOutputStream) {
        this(metaDataHolder, null, innerOutputStream);
    }

    /**
     * Use the attributes from the supplied attributeMap
     */
    public ZipOutput(final AttributeMap attributeMap,
                     final OutputStream innerOutputStream) {
        this(null, attributeMap, innerOutputStream);
    }

    private ZipOutput(final MetaDataHolder metaDataHolder,
                      final AttributeMap attributeMap,
                      final OutputStream innerOutputStream) {
        this.metaDataHolder = metaDataHolder;
        this.attributeMap = attributeMap;

        count = 0;
        zipOutputStream = new ZipArchiveOutputStream(innerOutputStream) {
            @Override
            public void close() throws IOException {
                ZipOutput.this.endZipEntry();
                super.close();
            }
        };
        zipOutputStream.setUseZip64(Zip64Mode.Always);
        outputStream = new ByteCountOutputStream(zipOutputStream);
    }

    @Override
    public void startZipEntry() throws IOException {
        endZipEntry();

        count++;

        // Write meta.
        final String base = Strings.padStart(Long.toString(count), 10, '0');
        String dataFileName = base + DATA_EXTENSION;
        String metaFileName = base + META_EXTENSION;

        final AttributeMap effectiveAttributeMap;
        if (attributeMap != null) {
            // A passed in attributeMap trumps the stream metadata
            effectiveAttributeMap = attributeMap;
            LOGGER.debug("Using attributeMap: {}", attributeMap);
        } else if (metaDataHolder != null) {
            effectiveAttributeMap = metaDataHolder.getMetaData();
            LOGGER.debug("Using metaDataHolder: {}", effectiveAttributeMap);
        } else {
            effectiveAttributeMap = null;
        }

        if (effectiveAttributeMap != null) {
            // TODO : I'm not sure where/who is setting fileName in meta so will leave for now.
            final String fileName = effectiveAttributeMap.get("fileName");
            if (!NullSafe.isBlankString(fileName)) {
                dataFileName = fileName;
                final int index = fileName.lastIndexOf(".");
                if (index != -1) {
                    metaFileName = fileName.substring(0, index) + META_EXTENSION;
                } else {
                    metaFileName = fileName + META_EXTENSION;
                }
            }

            zipOutputStream.putArchiveEntry(new ZipArchiveEntry(metaFileName));
            AttributeMapUtil.write(effectiveAttributeMap, zipOutputStream);
            zipOutputStream.closeArchiveEntry();
        }

        currentZipEntry = new ZipArchiveEntry(dataFileName);
        zipOutputStream.putArchiveEntry(currentZipEntry);
    }

    @Override
    public void endZipEntry() throws IOException {
        if (currentZipEntry != null) {
            zipOutputStream.closeArchiveEntry();
            currentZipEntry = null;
        }
    }

    @Override
    public boolean isZip() {
        return true;
    }

    @Override
    public long getCurrentOutputSize() {
        return outputStream.getCount();
    }

    @Override
    public boolean getHasBytesWritten() {
        return outputStream.getHasBytesWritten();
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}

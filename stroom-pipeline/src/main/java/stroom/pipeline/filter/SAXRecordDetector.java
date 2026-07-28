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

package stroom.pipeline.filter;

import stroom.pipeline.stepping.capture.RecordDetector;
import stroom.pipeline.stepping.capture.SteppingController;

import org.xml.sax.SAXException;

public class SAXRecordDetector extends AbstractXMLFilter implements RecordDetector {

    private SteppingController controller;

    private long currentStepIndex = -1;
    // The record index the first replayed record should be reported as. Zero for a normal run, which counts
    // records from the start of the stream. A replay that fires a single record from the middle of a stream
    // sets it, so the record is captured under the index it actually has rather than as record 0.
    private long baseRecordIndex;

    /**
     * Report the first record of the next stream as {@code baseRecordIndex} rather than 0.
     */
    public void setBaseRecordIndex(final long baseRecordIndex) {
        this.baseRecordIndex = baseRecordIndex;
    }

    @Override
    public void startStream() {
        if (controller != null) {
            currentStepIndex = baseRecordIndex - 1;
            controller.resetSourceLocation();
        }
        super.startStream();
    }

    @Override
    public void startDocument() throws SAXException {
        currentStepIndex++;
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();

        // Tell the controller that this is the end of a record.
        if (controller != null && controller.endRecord(currentStepIndex)) {
            throw new ExitSteppingException();
        }
    }

    @Override
    public void setController(final SteppingController controller) {
        this.controller = controller;
    }
}

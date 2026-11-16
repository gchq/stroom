/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.stepping.capture;

import stroom.meta.shared.Meta;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.stepping.fingerprint.ElementFingerprints;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.StepDataStoreException;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.pipeline.xsltfunctions.TaskScopeMap;
import stroom.task.api.SimpleTaskContext;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// That a backbone capture stops short of the store's byte cap, leaving the rest for the records a step
/// materialises, while any other capture can still use the whole cap (gh-5986).
class TestSteppingControllerBackbone {

    private static final long META_ID = 42L;
    private static final long MAX_BYTES = 10_000L;
    private static final ElementId PARSER = new ElementId("combinedParser");

    @Test
    void testABackboneIsRefusedOnceTheStoreHoldsItsShare(@TempDir final Path tempDir) {
        final StepDataStore store = storeFilledPastTheBackboneShare(tempDir);
        final SteppingController controller = controller(store);
        controller.setBackbone(true);

        assertThatThrownBy(() -> controller.endRecord(0))
                .isInstanceOf(StepDataStoreException.class)
                .hasMessageContaining(MAX_BYTES + " byte limit")
                .hasMessageContaining("narrow your selection");
    }

    @Test
    void testAnotherCaptureIsNotHeldToTheBackboneShare(@TempDir final Path tempDir) {
        final StepDataStore store = storeFilledPastTheBackboneShare(tempDir);
        final SteppingController controller = controller(store);
        controller.setBackbone(false);

        assertThat(controller.endRecord(0))
                .as("a capture that is not a backbone carries on")
                .isFalse();
    }

    @Test
    void testACaptureIsNotABackboneByDefault(@TempDir final Path tempDir) {
        final StepDataStore store = storeFilledPastTheBackboneShare(tempDir);
        final SteppingController controller = controller(store);

        assertThat(controller.endRecord(0))
                .as("a controller nobody marked as a backbone is not held to the backbone share")
                .isFalse();
    }

    @Test
    void testABackboneCarriesOnWhileThereIsHeadroom(@TempDir final Path tempDir) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(Long.toString(META_ID)), config());
        final SteppingController controller = controller(store);
        controller.setBackbone(true);

        assertThat(controller.endRecord(0))
                .as("an empty store has headroom for a backbone")
                .isFalse();
    }

    // A store holding the backbone's share of MAX_BYTES, written directly rather than by a capture: small
    // records are added until the store refuses a backbone.
    private StepDataStore storeFilledPastTheBackboneShare(final Path tempDir) {
        final StepDataStore store = new StepDataStore(tempDir.resolve(Long.toString(META_ID)), config());
        final CapturedElementData data = new CapturedElementData(
                CapturedData.text("x".repeat(40)), CapturedData.text("y".repeat(40)),
                false, false, true, null);
        long record = 0;
        while (hasBackboneHeadroom(store)) {
            assertThat(record).as("the store refuses a backbone before 1,000 small records").isLessThan(1_000);
            store.putElementData(new StepLocation(META_ID, 0L, record), PARSER, "fp", data);
            record++;
        }
        return store;
    }

    private static boolean hasBackboneHeadroom(final StepDataStore store) {
        try {
            store.checkBackboneHeadroom();
            return true;
        } catch (final StepDataStoreException e) {
            return false;
        }
    }

    private static SteppingConfig config() {
        return new SteppingConfig(null, null, MAX_BYTES, null, null, null, null, null, null, null, null, null);
    }

    // As TestSteppingControllerTermination's, with no monitors registered: a captured record writes
    // nothing, so endRecord exercises only the decision to write, not what is written.
    private SteppingController controller(final StepDataStore store) {
        final MetaHolder metaHolder = new MetaHolder();
        metaHolder.setMeta(Meta.builder().id(META_ID).build());
        metaHolder.setPartIndex(0);

        final SteppingController controller = new SteppingController(
                metaHolder, new ErrorReceiverProxy(), new LocationHolder(metaHolder), new TaskScopeMap());
        controller.setTaskContext(new SimpleTaskContext());
        controller.setRequest(PipelineStepRequest.builder().build());
        controller.setCaptureTarget(store, new ElementFingerprints(Map.of(), Map.of()), null);
        return controller;
    }
}

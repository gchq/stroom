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

package stroom.pipeline.stepping.fingerprint;

import stroom.docref.DocRef;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.stepping.store.StepDataStore;
import stroom.pipeline.stepping.store.SteppingConfig;
import stroom.util.shared.ElementId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestElementFingerprinter {

    private static final String SOURCE = "Source";
    private static final String PARSER = "parser";
    private static final String XSLT = "xslt";
    private static final String WRITER = "writer";

    private final ElementFingerprinter fingerprinter = new ElementFingerprinter();

    /**
     * Source -> parser -> xslt -> writer, with one property on the xslt.
     */
    private PipelineDataBuilder baseChain() {
        return new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement(XSLT, "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addProperty(new PipelineProperty(XSLT, "xslt", new PipelinePropertyValue("XSLT_A")))
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, XSLT)
                .addLink(XSLT, WRITER);
    }

    private ElementFingerprints fingerprint(final PipelineData data, final Map<String, String> code) {
        return fingerprinter.fingerprint(data, code);
    }

    @Test
    void testStableAcrossRepeatedComputation() {
        final PipelineData data = baseChain().build();
        final ElementFingerprints a = fingerprint(data, Collections.emptyMap());
        final ElementFingerprints b = fingerprint(data, Collections.emptyMap());

        for (final String id : new String[]{SOURCE, PARSER, XSLT, WRITER}) {
            assertThat(a.getOwnFingerprint(id)).isEqualTo(b.getOwnFingerprint(id));
            assertThat(a.getCumulativeFingerprint(id)).isEqualTo(b.getCumulativeFingerprint(id));
        }
    }

    @Test
    void testStableIrrespectiveOfBuildOrder() {
        final PipelineData ordered = baseChain().build();

        // Same pipeline, elements/links/properties added in a different order.
        final PipelineData shuffled = new PipelineDataBuilder()
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addElement(new PipelineElement(XSLT, "XSLTFilter"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addLink(XSLT, WRITER)
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, XSLT)
                .addProperty(new PipelineProperty(XSLT, "xslt", new PipelinePropertyValue("XSLT_A")))
                .build();

        final ElementFingerprints a = fingerprint(ordered, Collections.emptyMap());
        final ElementFingerprints b = fingerprint(shuffled, Collections.emptyMap());
        assertThat(a.getCumulativeFingerprints()).isEqualTo(b.getCumulativeFingerprints());
    }

    @Test
    void testPropertyChangeInvalidatesElementAndDownstreamOnly() {
        final ElementFingerprints before = fingerprint(baseChain().build(), Collections.emptyMap());

        final PipelineData edited = new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement(XSLT, "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addProperty(new PipelineProperty(XSLT, "xslt", new PipelinePropertyValue("XSLT_B")))
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, XSLT)
                .addLink(XSLT, WRITER)
                .build();
        final ElementFingerprints after = fingerprint(edited, Collections.emptyMap());

        // Own fingerprint changes only for the edited element.
        assertThat(after.getOwnFingerprint(XSLT)).isNotEqualTo(before.getOwnFingerprint(XSLT));
        assertThat(after.getOwnFingerprint(SOURCE)).isEqualTo(before.getOwnFingerprint(SOURCE));
        assertThat(after.getOwnFingerprint(PARSER)).isEqualTo(before.getOwnFingerprint(PARSER));
        assertThat(after.getOwnFingerprint(WRITER)).isEqualTo(before.getOwnFingerprint(WRITER));

        // Cumulative changes for the edited element and everything downstream, but not upstream.
        assertThat(after.getCumulativeFingerprint(SOURCE)).isEqualTo(before.getCumulativeFingerprint(SOURCE));
        assertThat(after.getCumulativeFingerprint(PARSER)).isEqualTo(before.getCumulativeFingerprint(PARSER));
        assertThat(after.getCumulativeFingerprint(XSLT)).isNotEqualTo(before.getCumulativeFingerprint(XSLT));
        assertThat(after.getCumulativeFingerprint(WRITER)).isNotEqualTo(before.getCumulativeFingerprint(WRITER));
    }

    @Test
    void testUpstreamChangePropagatesDownstream() {
        final ElementFingerprints before = fingerprint(baseChain().build(), Collections.emptyMap());

        // Add a property to the parser (upstream of xslt and writer).
        final PipelineData edited = baseChain()
                .addProperty(new PipelineProperty(PARSER, "type", new PipelinePropertyValue("XML")))
                .build();
        final ElementFingerprints after = fingerprint(edited, Collections.emptyMap());

        assertThat(after.getCumulativeFingerprint(SOURCE)).isEqualTo(before.getCumulativeFingerprint(SOURCE));
        assertThat(after.getOwnFingerprint(PARSER)).isNotEqualTo(before.getOwnFingerprint(PARSER));
        assertThat(after.getCumulativeFingerprint(PARSER)).isNotEqualTo(before.getCumulativeFingerprint(PARSER));
        assertThat(after.getCumulativeFingerprint(XSLT)).isNotEqualTo(before.getCumulativeFingerprint(XSLT));
        assertThat(after.getCumulativeFingerprint(WRITER)).isNotEqualTo(before.getCumulativeFingerprint(WRITER));
    }

    @Test
    void testInjectedCodeAffectsFingerprint() {
        final PipelineData data = baseChain().build();
        final ElementFingerprints v1 = fingerprint(data, Map.of(XSLT, "template-v1"));
        final ElementFingerprints v2 = fingerprint(data, Map.of(XSLT, "template-v2"));
        final ElementFingerprints noCode = fingerprint(data, Collections.emptyMap());

        assertThat(v1.getOwnFingerprint(XSLT)).isNotEqualTo(v2.getOwnFingerprint(XSLT));
        assertThat(v1.getOwnFingerprint(XSLT)).isNotEqualTo(noCode.getOwnFingerprint(XSLT));
        // Downstream cumulative reflects the injected code change.
        assertThat(v1.getCumulativeFingerprint(WRITER)).isNotEqualTo(v2.getCumulativeFingerprint(WRITER));
        // Upstream unaffected.
        assertThat(v1.getCumulativeFingerprint(PARSER)).isEqualTo(v2.getCumulativeFingerprint(PARSER));
    }

    /**
     * The point of fingerprint-addressed storage: after editing then reverting an element, the store
     * still holds the IO under the original fingerprint, so it is reused rather than reprocessed. This
     * drives the fingerprinter AND the store together (the guarantee lives across both).
     */
    @Test
    void testRevertReusesPersistedIo(@TempDir final Path tempDir) {
        final ElementId writerId = new ElementId(WRITER);
        final StepLocation loc = new StepLocation(1L, 0, 0);
        final StepDataStore store = new StepDataStore(tempDir.resolve("1"), new SteppingConfig());

        // Capture the writer's IO under config A's fingerprint.
        final String fpA = fingerprint(baseChain().build(), Collections.emptyMap())
                .getCumulativeFingerprint(WRITER);
        store.putElementData(loc, writerId, fpA, new CapturedElementData(CapturedData.text("in"), CapturedData.text("outputForA"), false, false, true, null));

        // Edit the xslt -> the writer's cumulative fingerprint changes; capture new IO under it.
        final String fpB = fingerprint(baseChain().addProperty(
                        new PipelineProperty(XSLT, "xslt", new PipelinePropertyValue("XSLT_B"))).build(),
                Collections.emptyMap()).getCumulativeFingerprint(WRITER);
        assertThat(fpB).isNotEqualTo(fpA);
        store.putElementData(loc, writerId, fpB, new CapturedElementData(CapturedData.text("in"), CapturedData.text("outputForB"), false, false, true, null));

        // Revert to config A: the recomputed fingerprint matches fpA and the original IO is still present.
        final String fpReverted = fingerprint(baseChain().build(), Collections.emptyMap())
                .getCumulativeFingerprint(WRITER);
        assertThat(fpReverted).isEqualTo(fpA);
        assertThat(store.hasElement(writerId, fpReverted)).isTrue();
        assertThat(store.getElementData(loc, writerId, fpReverted))
                .map(CapturedElementData::outputText)
                .contains("outputForA");
    }

    @Test
    void testValueTypeUnionDoesNotCollide() {
        final PipelineData asString = baseChain()
                .addProperty(new PipelineProperty(PARSER, "p", new PipelinePropertyValue("5")))
                .build();
        final PipelineData asInteger = baseChain()
                .addProperty(new PipelineProperty(PARSER, "p", new PipelinePropertyValue(Integer.valueOf(5))))
                .build();

        assertThat(fingerprint(asString, Collections.emptyMap()).getOwnFingerprint(PARSER))
                .isNotEqualTo(fingerprint(asInteger, Collections.emptyMap()).getOwnFingerprint(PARSER));
    }

    @Test
    void testDocRefValuedPropertyIsDeterministicAndSensitive() {
        final DocRef refA = new DocRef("Dictionary", "uuid-a", "dictA");
        final DocRef refB = new DocRef("Dictionary", "uuid-b", "dictB");

        final PipelineData withA = baseChain()
                .addProperty(new PipelineProperty(XSLT, "ref", new PipelinePropertyValue(refA)))
                .build();
        final PipelineData withA2 = baseChain()
                .addProperty(new PipelineProperty(XSLT, "ref", new PipelinePropertyValue(refA)))
                .build();
        final PipelineData withB = baseChain()
                .addProperty(new PipelineProperty(XSLT, "ref", new PipelinePropertyValue(refB)))
                .build();

        // Deterministic for equal DocRef values, sensitive to a different DocRef.
        assertThat(fingerprint(withA, Collections.emptyMap()).getOwnFingerprint(XSLT))
                .isEqualTo(fingerprint(withA2, Collections.emptyMap()).getOwnFingerprint(XSLT));
        assertThat(fingerprint(withA, Collections.emptyMap()).getOwnFingerprint(XSLT))
                .isNotEqualTo(fingerprint(withB, Collections.emptyMap()).getOwnFingerprint(XSLT));
    }

    @Test
    void testSiblingForkBranchIsolation() {
        final PipelineDataBuilder fork = new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement("xsltA", "XSLTFilter"))
                .addElement(new PipelineElement("xsltB", "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addProperty(new PipelineProperty("xsltA", "xslt", new PipelinePropertyValue("A1")))
                .addProperty(new PipelineProperty("xsltB", "xslt", new PipelinePropertyValue("B1")))
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, "xsltA")
                .addLink(PARSER, "xsltB")
                .addLink("xsltA", WRITER)
                .addLink("xsltB", WRITER);
        final ElementFingerprints before = fingerprint(fork.build(), Collections.emptyMap());

        // Edit only xsltA.
        final PipelineDataBuilder editedFork = new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement("xsltA", "XSLTFilter"))
                .addElement(new PipelineElement("xsltB", "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addProperty(new PipelineProperty("xsltA", "xslt", new PipelinePropertyValue("A2")))
                .addProperty(new PipelineProperty("xsltB", "xslt", new PipelinePropertyValue("B1")))
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, "xsltA")
                .addLink(PARSER, "xsltB")
                .addLink("xsltA", WRITER)
                .addLink("xsltB", WRITER);
        final ElementFingerprints after = fingerprint(editedFork.build(), Collections.emptyMap());

        // The sibling branch is completely unaffected; the edited branch and the shared writer change.
        assertThat(after.getCumulativeFingerprint("xsltB")).isEqualTo(before.getCumulativeFingerprint("xsltB"));
        assertThat(after.getOwnFingerprint("xsltB")).isEqualTo(before.getOwnFingerprint("xsltB"));
        assertThat(after.getCumulativeFingerprint("xsltA")).isNotEqualTo(before.getCumulativeFingerprint("xsltA"));
        assertThat(after.getCumulativeFingerprint(WRITER)).isNotEqualTo(before.getCumulativeFingerprint(WRITER));
    }

    @Test
    void testPhantomLinkIgnored() {
        final ElementFingerprints base = fingerprint(baseChain().build(), Collections.emptyMap());

        // Add a link from an id that has no element; it must be ignored, not injected as an element.
        final ElementFingerprints withGhost = fingerprint(
                baseChain().addLink("ghostElement", WRITER).build(), Collections.emptyMap());

        assertThat(withGhost.getElementIds()).doesNotContain("ghostElement");
        assertThat(withGhost.getCumulativeFingerprints()).isEqualTo(base.getCumulativeFingerprints());
    }

    @Test
    void testCycleThrows() {
        final PipelineData cyclic = new PipelineDataBuilder()
                .addElement(new PipelineElement("a", "XSLTFilter"))
                .addElement(new PipelineElement("b", "XSLTFilter"))
                .addLink("a", "b")
                .addLink("b", "a")
                .build();

        assertThatThrownBy(() -> fingerprint(cyclic, Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void testForkMultiInputCombinesDeterministically() {
        // writer fed by two xslts: a and b.
        final PipelineDataBuilder forkOrder1 = new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement("xsltA", "XSLTFilter"))
                .addElement(new PipelineElement("xsltB", "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addLink(SOURCE, PARSER)
                .addLink(PARSER, "xsltA")
                .addLink(PARSER, "xsltB")
                .addLink("xsltA", WRITER)
                .addLink("xsltB", WRITER);

        final PipelineDataBuilder forkOrder2 = new PipelineDataBuilder()
                .addElement(new PipelineElement(SOURCE, "Source"))
                .addElement(new PipelineElement(PARSER, "CombinedParser"))
                .addElement(new PipelineElement("xsltB", "XSLTFilter"))
                .addElement(new PipelineElement("xsltA", "XSLTFilter"))
                .addElement(new PipelineElement(WRITER, "XMLWriter"))
                .addLink(SOURCE, PARSER)
                .addLink("xsltB", WRITER)
                .addLink("xsltA", WRITER)
                .addLink(PARSER, "xsltB")
                .addLink(PARSER, "xsltA");

        final ElementFingerprints a = fingerprint(forkOrder1.build(), Collections.emptyMap());
        final ElementFingerprints b = fingerprint(forkOrder2.build(), Collections.emptyMap());
        assertThat(a.getCumulativeFingerprint(WRITER)).isEqualTo(b.getCumulativeFingerprint(WRITER));
    }
}

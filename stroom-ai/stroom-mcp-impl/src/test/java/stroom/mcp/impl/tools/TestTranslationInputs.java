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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.processing.TranslationInputText;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/// A build's records at its translation's input: read only for a pipeline described in place whose translation a
/// pre-processor feeds, each stream stepped to the translation element and its input captured.
class TestTranslationInputs {

    private static final String JSON = "xmlns=\"http://www.w3.org/2013/XSL/json\"";

    private final BuildWorld world = new BuildWorld();
    private final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    private final TranslationInputs inputs = new TranslationInputs(world::records, stepping);

    @Test
    void onlyAPreProcessedTranslationDescribedInPlaceIsReadAtItsInput() {
        world.folder("System/" + BuildWorld.WORKSPACE + "/vpn");
        assertThat(inputs.forBuild("vpn")).isEmpty();
        assertThat(inputs.forBuild(null)).isEmpty();

        world.records().rememberStartFrom("vpn", startFrom(false));
        assertThat(inputs.forBuild("vpn")).isEmpty();

        world.records().rememberStartFrom("vpn", startFrom(true));
        assertThat(inputs.forBuild("vpn")).contains(new TranslationInputs.At("p", "translationFilter"));
    }

    @Test
    void aCopyOfAPreProcessedPipelineIsReadAtItsInputAndARawSurveyOfItIsStale() {
        final Map<String, Object> copied = Map.of("choice", ExistingTranslations.EXISTING, "pipeline",
                Map.of("uuid", "p"), InPlaceInduction.ELEMENT, "translationFilter", InPlaceInduction.PRE_PROCESSED,
                true);

        assertThat(TranslationInputs.at(copied)).contains(new TranslationInputs.At("p", "translationFilter"));
        assertThat(TranslationInputs.at(Map.of("choice", ExistingTranslations.DRAFT))).isEmpty();
        assertThat(TranslationInputs.at(null)).isEmpty();
        assertThat(TranslationInputs.staleSurvey(copied, Map.of("saturated", true))).isTrue();
        assertThat(TranslationInputs.staleSurvey(copied, Map.of(SurveyDoc.READ_AT,
                SurveyDoc.AT_TRANSLATION_INPUT))).isFalse();
        assertThat(TranslationInputs.staleSurvey(copied, null)).isFalse();
        assertThat(TranslationInputs.staleSurvey(Map.of("choice", ExistingTranslations.DRAFT), Map.of())).isFalse();
    }

    @Test
    void eachStreamIsSteppedToTheTranslationAndItsInputMadeOneText() {
        final SteppedPipeline loaded = Mockito.mock(SteppedPipeline.class);
        Mockito.when(loaded.name()).thenReturn("VPN-Events");
        Mockito.when(stepping.load("p")).thenReturn(loaded);
        Mockito.when(stepping.outputs(any(), eq(loaded), eq(List.of(7L)), eq("translationFilter"), isNull(),
                anyInt())).thenReturn(new PipelineStepping.Outputs(Map.of("7:0", "", "7:1", ""), Map.of(
                "7:0", "<map " + JSON + "><string key=\"action\">login</string></map>",
                "7:1", "<map " + JSON + "><string key=\"action\">logout</string></map>"), false));
        Mockito.when(stepping.outputs(any(), eq(loaded), eq(List.of(8L)), eq("translationFilter"), isNull(),
                anyInt())).thenReturn(new PipelineStepping.Outputs(Map.of(), Map.of(), false));

        final Map<Long, TranslationInputText.Text> texts = inputs.texts(BuildToolCalls.context(),
                new TranslationInputs.At("p", "translationFilter"), List.of(7L, 8L), 50);

        // A stream giving nothing at the input is left out.
        assertThat(texts).containsOnlyKeys(7L);
        assertThat((List<?>) ToolJson.loads(texts.get(7L).text())).hasSize(2);
        Mockito.verify(stepping).checkCanStep();
        Mockito.verify(stepping).audit(any(), eq(List.of(7L)), any());
    }

    private static Map<String, Object> startFrom(final boolean preProcessed) {
        final Map<String, Object> described = new LinkedHashMap<>();
        described.put(InPlaceInduction.ELEMENT, "translationFilter");
        described.put(InPlaceInduction.PRE_PROCESSED, preProcessed);
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("choice", InPlaceInduction.IN_PLACE);
        out.put("pipeline", Map.of("name", "VPN-Events", "uuid", "p"));
        out.put(InPlaceInduction.IN_PLACE, described);
        return out;
    }
}

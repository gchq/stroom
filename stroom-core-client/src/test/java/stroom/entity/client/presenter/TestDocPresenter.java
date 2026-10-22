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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.widget.component.client.ComponentNode;
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;

import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.View;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocPresenter {

    private static final DocRef DOC_REF = new DocRef("Test", "uuid-1", "Test Doc");

    @Test
    void whatItOwnsFollowsWhetherTheDocumentIsReadOnly() {
        final Doc document = new Doc();
        final ComponentNode root = ComponentNode.root("tab");
        root.adopt(document.getComponentNode());
        // e.g. a settings presenter in one of its tabs
        final ComponentNode field = new ComponentNode("field");
        document.getComponentNode().adopt(field);
        final List<Boolean> readOnly = new ArrayList<>();
        field.watch(Contexts.READ_ONLY, readOnly::add);

        document.read(DOC_REF, "contents", false);
        document.read(DOC_REF, "contents", true);
        document.read(DOC_REF, "contents", false);

        // Read only until it is read, as nothing may be changed before then
        assertThat(readOnly).containsExactly(true, false, true, false);
        assertThat(field.lookup(Contexts.DOCUMENT)).isEqualTo(DOC_REF);
    }

    @Test
    void aDocumentEmbeddedInAnotherScreenIsToldWhetherItIsReadOnly() {
        final Doc document = new Doc();
        final ComponentNode root = ComponentNode.root("tab");
        root.adopt(document.getComponentNode());

        document.setReadOnlyWithoutReading(false);
        assertThat(document.getComponentNode().lookup(Contexts.READ_ONLY)).isFalse();
        assertThat(document.isReadOnly()).isFalse();

        document.setReadOnlyWithoutReading(true);
        assertThat(document.getComponentNode().lookup(Contexts.READ_ONLY)).isTrue();
    }

    @Test
    void aReadOnlyOwnerKeepsItReadOnly() {
        final Doc document = new Doc();
        final ComponentNode root = ComponentNode.root("tab");
        root.provide(Contexts.READ_ONLY, ContextValue.of(true));
        root.adopt(document.getComponentNode());

        document.read(DOC_REF, "contents", false);

        assertThat(document.getComponentNode().lookup(Contexts.READ_ONLY)).isTrue();
    }

    @Test
    void aPartThatBecomesDirtyTellsItsDocument() {
        final Doc document = new Doc();
        ComponentNode.root("tab").adopt(document.getComponentNode());
        final Doc part = new Doc();
        document.getComponentNode().adopt(part.getComponentNode());
        document.read(DOC_REF, "part", false);
        part.read(DOC_REF, "part", false);
        // Each writes what the part shows
        part.writer = doc -> part.text;
        document.writer = doc -> part.text;
        final List<Boolean> documentDirty = new ArrayList<>();
        document.addDirtyListener(documentDirty::add);

        part.text = "changed";
        part.onChange();

        assertThat(part.isDirty()).isTrue();
        assertThat(document.isDirty()).isTrue();
        assertThat(documentDirty).containsExactly(true);

        part.text = "part";
        part.onChange();

        assertThat(document.isDirty()).isFalse();
        assertThat(documentDirty).containsExactly(true, false);
    }

    @Test
    void anythingBelowADocumentCanTellItOfAChange() {
        final Doc document = new Doc();
        ComponentNode.root("tab").adopt(document.getComponentNode());
        document.read(DOC_REF, "contents", false);
        document.writer = doc -> "changed";
        // e.g. a field's node, two levels down
        final ComponentNode panel = new ComponentNode("panel");
        document.getComponentNode().adopt(panel);
        final ComponentNode field = new ComponentNode("field");
        panel.adopt(field);

        field.lookupAbove(Contexts.DIRTY).onChange();

        assertThat(document.isDirty()).isTrue();
    }

    @Test
    void aReadOnlyDocumentIsNeverDirty() {
        final Doc document = new Doc();
        ComponentNode.root("tab").adopt(document.getComponentNode());
        document.read(DOC_REF, "contents", true);
        document.writer = doc -> "changed";

        document.onChange();

        assertThat(document.isDirty()).isFalse();
    }

    // --------------------------------------------------------------------------------


    private static final class Doc extends DocPresenter<View, String> {

        private String text = "part";
        private UnaryOperator<String> writer = UnaryOperator.identity();

        private Doc() {
            super(new SimpleEventBus(), Mockito.mock(View.class));
        }

        void setReadOnlyWithoutReading(final boolean readOnly) {
            setDocumentReadOnly(readOnly);
        }

        @Override
        protected void onRead(final DocRef docRef, final String document, final boolean readOnly) {
            text = document;
        }

        @Override
        protected String onWrite(final String document) {
            return writer.apply(document);
        }
    }
}

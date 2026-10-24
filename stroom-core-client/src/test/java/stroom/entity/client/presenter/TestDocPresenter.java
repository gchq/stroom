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
import stroom.widget.component.client.ContextValue;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.presenter.client.View;
import stroom.widget.tab.client.presenter.TabData;

import com.google.web.bindery.event.shared.SimpleEventBus;
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
        final ControllerNode root = ControllerNode.root("tab");
        root.adopt(document.getControllerNode());
        // e.g. a settings presenter in one of its tabs
        final ControllerNode field = new ControllerNode("field");
        document.getControllerNode().adopt(field);
        final List<Boolean> readOnly = new ArrayList<>();
        field.watch(Contexts.READ_ONLY, readOnly::add);

        document.open(DOC_REF, "contents", false);
        document.open(DOC_REF, "contents", true);
        document.open(DOC_REF, "contents", false);

        // Read only until its opener says, as nothing may be changed before then
        assertThat(readOnly).containsExactly(true, false, true, false);
        assertThat(field.lookup(Contexts.DOCUMENT)).isEqualTo(DOC_REF);
    }

    @Test
    void aDocumentEmbeddedInAnotherScreenIsToldWhetherItIsReadOnly() {
        final Doc document = new Doc();
        final ControllerNode root = ControllerNode.root("tab");
        root.adopt(document.getControllerNode());

        document.setReadOnly(false);
        assertThat(document.getControllerNode().lookup(Contexts.READ_ONLY)).isFalse();
        assertThat(document.isReadOnly()).isFalse();

        document.setReadOnly(true);
        assertThat(document.getControllerNode().lookup(Contexts.READ_ONLY)).isTrue();
    }

    @Test
    void aReadOnlyOwnerKeepsItReadOnly() {
        final Doc document = new Doc();
        final ControllerNode root = ControllerNode.root("tab");
        root.provide(Contexts.READ_ONLY, ContextValue.of(true));
        root.adopt(document.getControllerNode());

        document.open(DOC_REF, "contents", false);

        assertThat(document.getControllerNode().lookup(Contexts.READ_ONLY)).isTrue();
    }

    @Test
    void aDocumentNobodySaysMayBeChangedIsReadOnly() {
        final Doc document = new Doc();
        ControllerNode.root("tab").adopt(document.getControllerNode());

        document.read(DOC_REF, "contents");

        assertThat(document.isReadOnly()).isTrue();
    }

    @Test
    void aDocumentThatIsPartOfAnotherIsReadOnlyAsThatIs() {
        final Doc document = new Doc();
        ControllerNode.root("tab").adopt(document.getControllerNode());
        // e.g. a settings tab, which is itself a document, read without being told
        final Doc part = new Doc();
        document.getControllerNode().adopt(part.getControllerNode());

        document.open(DOC_REF, "contents", false);
        part.read(DOC_REF, "contents");
        assertThat(part.isReadOnly()).isFalse();

        document.open(DOC_REF, "contents", true);
        part.read(DOC_REF, "contents");
        assertThat(part.isReadOnly()).isTrue();
        assertThat(part.readOnlyWhenRead).isTrue();
    }

    @Test
    void aTabIsOwnedBeforeItIsReadSoItReadsAsItsDocumentIs() {
        final Doc document = new Doc();
        ControllerNode.root("tab").adopt(document.getControllerNode());
        document.setReadOnly(false);
        final Doc tab = new Doc();
        final TabContentProvider<String> tabs = new TabContentProvider<>(new SimpleEventBus(), document);
        final TabData tabData = Mockito.mock(TabData.class);
        tabs.add(tabData, new AbstractTabProvider<String, Doc>() {
            @Override
            protected Doc createPresenter() {
                return tab;
            }

            @Override
            public void onRead(final Doc presenter, final DocRef docRef, final String document) {
                presenter.read(docRef, document);
            }
        });
        tabs.read(DOC_REF, "contents");

        // The user selects the tab once the document is read
        tabs.getPresenter(tabData);

        assertThat(tab.readOnlyWhenRead).isFalse();
        assertThat(tab.getControllerNode().parent()).isSameAs(document.getControllerNode());
    }

    @Test
    void aPartThatBecomesDirtyTellsItsDocument() {
        final Doc document = new Doc();
        ControllerNode.root("tab").adopt(document.getControllerNode());
        final Doc part = new Doc();
        document.getControllerNode().adopt(part.getControllerNode());
        document.open(DOC_REF, "part", false);
        part.open(DOC_REF, "part", false);
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
        ControllerNode.root("tab").adopt(document.getControllerNode());
        document.open(DOC_REF, "contents", false);
        document.writer = doc -> "changed";
        // e.g. a field's node, two levels down
        final ControllerNode panel = new ControllerNode("panel");
        document.getControllerNode().adopt(panel);
        final ControllerNode field = new ControllerNode("field");
        panel.adopt(field);

        field.lookupAbove(Contexts.DIRTY).onChange();

        assertThat(document.isDirty()).isTrue();
    }

    @Test
    void aReadOnlyDocumentIsNeverDirty() {
        final Doc document = new Doc();
        ControllerNode.root("tab").adopt(document.getControllerNode());
        document.open(DOC_REF, "contents", true);
        document.writer = doc -> "changed";

        document.onChange();

        assertThat(document.isDirty()).isFalse();
    }

    // --------------------------------------------------------------------------------


    private static final class Doc extends DocPresenter<View, String> {

        private String text = "part";
        private UnaryOperator<String> writer = UnaryOperator.identity();
        private Boolean readOnlyWhenRead;

        private Doc() {
            super(new SimpleEventBus(), Mockito.mock(View.class));
        }

        // As its plugin opens it
        void open(final DocRef docRef, final String document, final boolean readOnly) {
            setReadOnly(readOnly);
            read(docRef, document);
        }

        @Override
        protected void onRead(final DocRef docRef, final String document) {
            text = document;
            readOnlyWhenRead = isReadOnly();
        }

        @Override
        protected String onWrite(final String document) {
            return writer.apply(document);
        }
    }
}

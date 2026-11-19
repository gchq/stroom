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

package stroom.document.asset.client.component;

import stroom.document.asset.shared.DocumentAsset;
import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentAssetTreeItem {

    @Test
    void testFolderTakesChildrenAndSaysTheirDepth() {
        final DocumentAssetTreeItem js = DocumentAssetTreeItem.createNewFolderItem("js");
        final DocumentAssetTreeItem lib = DocumentAssetTreeItem.createNewFolderItem("lib");
        final DocumentAssetTreeItem d3 = file("a1", "d3.js");
        js.addItem(lib);
        lib.addItem(d3);

        assertThat(js.getChildCount()).isEqualTo(1);
        assertThat(js.getChild(0)).isSameAs(lib);
        assertThat(d3.getParentItem()).isSameAs(lib);
        assertThat(lib.getParentItem()).isSameAs(js);
        assertThat(js.getParentItem()).isNull();
        assertThat(js.getDepth()).isZero();
        assertThat(d3.getDepth()).isEqualTo(2);
    }

    @Test
    void testFileTakesNoChildren() {
        final DocumentAssetTreeItem chart = file("a1", "chart.js");
        final DocumentAssetTreeItem other = file("a2", "other.js");
        chart.addItem(other);

        assertThat(chart.getChildCount()).isZero();
        assertThat(other.getParentItem()).isNull();
    }

    @Test
    void testAddingMovesAnItemFromItsFolder() {
        final DocumentAssetTreeItem from = DocumentAssetTreeItem.createNewFolderItem("from");
        final DocumentAssetTreeItem to = DocumentAssetTreeItem.createNewFolderItem("to");
        final DocumentAssetTreeItem chart = file("a1", "chart.js");
        from.addItem(chart);
        to.addItem(chart);

        assertThat(from.getChildCount()).isZero();
        assertThat(to.getChildCount()).isEqualTo(1);
        assertThat(chart.getParentItem()).isSameAs(to);
    }

    @Test
    void testRemoveItemsLeavesChildrenWithoutAParent() {
        final DocumentAssetTreeItem js = DocumentAssetTreeItem.createNewFolderItem("js");
        final DocumentAssetTreeItem chart = file("a1", "chart.js");
        js.addItem(chart);
        js.removeItems();

        assertThat(js.getChildCount()).isZero();
        assertThat(chart.getParentItem()).isNull();
    }

    @Test
    void testOpenState() {
        final DocumentAssetTreeItem js = DocumentAssetTreeItem.createNewFolderItem("js");
        assertThat(js.getState()).isFalse();
        js.setState(true);
        assertThat(js.getState()).isTrue();
    }

    @Test
    void testLabelExistsIgnoresTheItemItself() {
        final DocumentAssetTreeItem js = DocumentAssetTreeItem.createNewFolderItem("js");
        js.addItem(file("a1", "chart.js"));

        assertThat(js.labelExists("chart.js", null)).isTrue();
        assertThat(js.labelExists("chart.js", "a2")).isTrue();
        assertThat(js.labelExists("chart.js", "a1")).isFalse();
        assertThat(js.labelExists("other.js", null)).isFalse();
    }

    @Test
    void testIconFromExtension() {
        assertThat(DocumentAssetTreeItem.createNewFolderItem("js").getIcon()).isEqualTo(SvgImage.FOLDER);
        assertThat(file("a1", "chart.JS").getIcon()).isEqualTo(SvgImage.FILE_SCRIPT);
        assertThat(file("a2", "logo.png").getIcon()).isEqualTo(SvgImage.FILE_IMAGE);
        assertThat(file("a3", "index.html").getIcon()).isEqualTo(SvgImage.FILE_FORMATTED);
        assertThat(file("a4", "notes.txt").getIcon()).isEqualTo(SvgImage.FILE);
        assertThat(file("a5", "README").getIcon()).isEqualTo(SvgImage.FILE);
    }

    @Test
    void testItemFromAsset() {
        final DocumentAssetTreeItem folder = DocumentAssetTreeItem.createItemFromAsset(
                new DocumentAsset("f1", "js", true), "js");

        assertThat(folder.getId()).isEqualTo("f1");
        assertThat(folder.isLeaf()).isFalse();
        assertThat(folder.getLabel()).isEqualTo("js");
        assertThat(file("a1", "chart.js").isLeaf()).isTrue();
    }

    private static DocumentAssetTreeItem file(final String id, final String name) {
        return DocumentAssetTreeItem.createItemFromAsset(new DocumentAsset(id, name, false), name);
    }
}

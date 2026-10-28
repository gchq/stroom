/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.DisabledState;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;

import java.util.Objects;
import java.util.Optional;

/// A pipeline element's box in the pipeline structure tree (see pipeline-element.css, a BEM block):
/// its type icon, name and any embedded document icon, and the dots where its connectors join it
/// (its ::before and ::after). It is an item of the tree for assistive technology, named by all
/// that the icons show (which are hover tips only).
public class PipelineElementBox extends Box<PipelineElement> {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String BLOCK = "pipeline-element";
    private static final String TARGET_CLASS = BLOCK + "--target";
    private static final String REFUSED_CLASS = BLOCK + "--refused";
    private static final String DETACHED_CLASS = BLOCK + "--detached";
    private static final String IMAGE_CLASS = BLOCK + "__image";
    private static final String TYPE_ICON_CLASS = BLOCK + "__type-icon";
    private static final String REFUSED_ICON_CLASS = BLOCK + "__refused-icon";
    private static final String EMBEDDED_CLASS = BLOCK + "__embedded";
    // What flows along the connectors into and out of it, which colour its dots
    private static final String FLOW_IN_ATTRIBUTE = "data-flow-in";
    private static final String FLOW_OUT_ATTRIBUTE = "data-flow-out";
    private static final String ARIA_HIDDEN = "aria-hidden";
    private static final String EMBEDDED_NAME = "has embedded document";
    private static final String INHERITED_EMBEDDED_NAME = "has embedded document inherited from parent pipeline";

    private final PipelineModel pipelineModel;
    private PipelineElement pipelineElement;
    // The element's name: a plain element, not GWT's Label (whose gwt-Label class isn't BEM)
    @UiField
    Element label;
    // Its type icon, if it has one, and the "not allowed" icon that takes its place while it refuses
    // what is being moved to it (so that that isn't told by colour alone), which is only in the
    // document then
    private final Element typeIcon;
    private final Element refusedIcon;
    private boolean refused;
    // The element's type, for its name as a tree item, e.g. "XSLT Filter"
    private final String typeName;
    // What its embedded document icon says, if it has one, e.g. "has embedded document"
    private String embeddedName;
    private boolean detached;
    // While an element is detached, its name, and whether this one can take it, for this one's name
    private String detachedName;
    private boolean canTake;

    public PipelineElementBox(final PipelineModel pipelineModel,
                              final PipelineElement pipelineElement,
                              final SvgImage icon,
                              final String iconName) {
//        GWT.log("Creating pipe element " + pipelineElement.getId());
        this.pipelineModel = pipelineModel;
        this.pipelineElement = pipelineElement;
        this.typeName = iconName;

        // The box and its name (see the template)
        final HTMLPanel background = BINDER.createAndBindUi(this);
        setLabelText(pipelineElement.getDisplayName());
        setDescription(pipelineElement.getDescription());

        if (icon != null) {
            typeIcon = createIcon(icon, TYPE_ICON_CLASS);
            // Its type is in the box's name (see updateName)
            hideIcon(typeIcon, iconName);
            // Before the name
            background.getElement().insertFirst(typeIcon);
        } else {
            typeIcon = null;
        }
        // Its name says so too (see setDetachedName)
        refusedIcon = createIcon(SvgImage.NOT_ALLOWED, REFUSED_ICON_CLASS);
        hideIcon(refusedIcon, null);


        final Optional<PipelineProperty> embeddedProperty = pipelineModel.getProperties(pipelineElement).stream()
                .filter(Objects::nonNull)
                .filter(p -> p.getValue() != null && p.getValue().getEntity() != null &&
                        p.getValue().isEmbedded())
                .findAny();

        if (embeddedProperty.isPresent()) {
            final boolean ownEmbedded = pipelineModel.getPipelineLayer().getPipelineData().getProperties() != null
                    && pipelineModel.getPipelineLayer().getPipelineData().getProperties().getAdd() != null
                    && pipelineModel.getPipelineLayer().getPipelineData().getProperties().getAdd().contains(
                            embeddedProperty.get());
            embeddedName = ownEmbedded
                    ? EMBEDDED_NAME
                    : INHERITED_EMBEDDED_NAME;
            final Element embeddedIcon = createIcon(ownEmbedded
                            ? SvgImage.CODE
                            : SvgImage.EXPLORER,
                    EMBEDDED_CLASS + " icon-colour__blue");
            // What it says is in the box's name (see updateName)
            hideIcon(embeddedIcon, capitalise(embeddedName));
            background.getElement().appendChild(embeddedIcon);
        }

        initWidget(background);
        updateName();
    }

    /// Selects (or not) the element: `aria-selected`, which its style follows.
    ///
    /// @param selected Whether it is selected.
    @Override
    public void setSelected(final boolean selected) {
        getElement().setAttribute("aria-selected", String.valueOf(selected));
    }

    /// Makes the box an item of the tree for assistive technology (`role="treeitem"`), named by its
    /// element's name and type, at its place in the tree, and whether it is the tree's one tab
    /// stop.
    ///
    /// @param level       Its depth in the tree, from 1 for the source.
    /// @param position    Its position among its siblings, from 1.
    /// @param count       How many siblings it has, itself included.
    /// @param hasChildren Whether it has children (which are always shown).
    /// @param tabStop     Whether it is the tree's tab stop.
    public void setTreeItem(final int level,
                            final int position,
                            final int count,
                            final boolean hasChildren,
                            final boolean tabStop) {
        final Element element = getElement();
        element.setAttribute("role", "treeitem");
        element.setAttribute("aria-level", String.valueOf(level));
        element.setAttribute("aria-posinset", String.valueOf(position));
        element.setAttribute("aria-setsize", String.valueOf(count));
        // Always expanded, as the tree can't be collapsed, but kept deliberately: in a flat tree
        // (items with aria-level, not nested groups) it is what tells assistive technology that an
        // item has children, which an item without it (an end item) doesn't. Left goes to the
        // parent, as on a collapsed or end item in the ARIA tree pattern, rather than collapsing
        if (hasChildren) {
            element.setAttribute("aria-expanded", "true");
        } else {
            element.removeAttribute("aria-expanded");
        }
        element.setAttribute("tabindex", tabStop
                ? "0"
                : "-1");
        updateName();
    }

    /// Shows (or stops showing) that the element is detached, to be attached to another parent:
    /// faded, with a dashed border, and said so in its name.
    ///
    /// @param detached Whether it is detached.
    @Override
    public void setDetached(final boolean detached) {
        this.detached = detached;
        toggleClass(DETACHED_CLASS, detached);
        updateName();
    }

    /// Says in the element's name whether it can take an element detached to be attached to another
    /// parent, e.g. "indexWriter, XML Writer, can't take rawWriter", so that it is known before trying.
    ///
    /// @param detachedName The detached element's name, or null when none is detached (or this is
    ///                     it, or after it).
    /// @param canTake      Whether this element can take it.
    @Override
    public void setDetachedName(final String detachedName, final boolean canTake) {
        this.detachedName = detachedName;
        this.canTake = canTake;
        updateName();
    }

    // e.g. "translation, XSLT Filter", "translation, XSLT Filter, has embedded document",
    // "rawWriter, XML Writer, detached" or "decoration, XSLT Filter, can take rawWriter"
    private void updateName() {
        if (getWidget() == null) {
            // Not built yet: the constructor names it once it is
            return;
        }
        final StringBuilder sb = new StringBuilder(NullSafe.string(label.getInnerText()));
        if (NullSafe.isNonBlankString(typeName) && !typeName.equals(label.getInnerText())) {
            sb.append(", ").append(typeName);
        }
        if (embeddedName != null) {
            sb.append(", ").append(embeddedName);
        }
        if (detached) {
            sb.append(", detached");
        }
        if (detachedName != null) {
            sb.append(canTake
                            ? ", can take "
                            : ", can't take ")
                    .append(detachedName);
        }
        getElement().setAttribute("aria-label", sb.toString());
    }

    @Override
    public void showHotspot(final boolean show) {
        toggleClass(TARGET_CLASS, show);
    }

    @Override
    public void showRefused(final boolean refused) {
        toggleClass(REFUSED_CLASS, refused);
        if (refused != this.refused) {
            this.refused = refused;
            // The "not allowed" icon takes the type icon's place (or the start, if it has none)
            final Element shown = refused
                    ? typeIcon
                    : refusedIcon;
            final Element showing = refused
                    ? refusedIcon
                    : typeIcon;
            if (shown != null) {
                getElement().replaceChild(showing, shown);
            } else if (refused) {
                getElement().insertFirst(refusedIcon);
            } else {
                refusedIcon.removeFromParent();
            }
        }
    }

    /// Disables (or not) the element: `aria-disabled`, which its style follows.
    ///
    /// @param disabled Whether it is disabled.
    public void setDisabled(final boolean disabled) {
        if (disabled) {
            getElement().setAttribute(DisabledState.ARIA_DISABLED, "true");
        } else {
            getElement().removeAttribute(DisabledState.ARIA_DISABLED);
        }
    }

    // An icon: the SVG in SvgImageUtil's div (its own element, not in one of ours), with the given
    // classes too
    private static Element createIcon(final SvgImage svgImage, final String className) {
        final Element holder = Document.get().createDivElement();
        holder.setInnerSafeHtml(SvgImageUtil.toSafeHtml(svgImage, "svgIcon", IMAGE_CLASS, className));
        final Element icon = holder.getFirstChildElement();
        icon.removeFromParent();
        return icon;
    }

    // An icon whose meaning is in the box's name: hidden from assistive technology, with a hover
    // tip for those who can see it
    private static void hideIcon(final Element icon, final String tip) {
        icon.setAttribute(ARIA_HIDDEN, "true");
        if (!NullSafe.isBlankString(tip)) {
            icon.setTitle(tip);
        }
    }

    private static String capitalise(final String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    // The element's description as its name's hover tip, if it has one
    private void setDescription(final String description) {
        if (NullSafe.isBlankString(description)) {
            label.removeAttribute("title");
        } else {
            label.setAttribute("title", description);
        }
    }

    /// Shows the dots where the box's connectors join it, over its borders, coloured by what flows
    /// along them (see [PipelineConnectorRenderer#getFlow]): one on its left if it has a parent,
    /// and one on its right if it has children.
    ///
    /// @param hasChildren Whether the element has children in the tree shown.
    public void setPorts(final boolean hasChildren) {
        final PipelineElement parent = NullSafe.get(pipelineModel.getParentMap(), map -> map.get(pipelineElement));
        setFlow(FLOW_IN_ATTRIBUTE, parent == null
                ? null
                : PipelineConnectorRenderer.getFlow(pipelineModel.getElementType(parent)));
        setFlow(FLOW_OUT_ATTRIBUTE, hasChildren
                ? PipelineConnectorRenderer.getFlow(pipelineModel.getElementType(pipelineElement))
                : null);
    }

    // What flows along a side's connectors, for its dot's colour (none without)
    private void setFlow(final String attribute, final PipelineConnectorRenderer.Flow flow) {
        if (flow == null) {
            getElement().removeAttribute(attribute);
        } else {
            getElement().setAttribute(attribute, flow.getName());
        }
    }

    public void refresh(final PipelineElement pipelineElement) {
        this.pipelineElement = pipelineElement;
        setLabelText(pipelineElement.getName() != null
                ? pipelineElement.getName()
                : pipelineElement.getId());
        setDescription(pipelineElement.getDescription());
    }

    // The element's name, also in data-text, so that the label keeps room for it in bold (see
    // pipeline-element.css)
    private void setLabelText(final String text) {
        label.setInnerText(text);
        label.setAttribute("data-text", text == null
                ? ""
                : text);
        updateName();
    }

    private void toggleClass(final String className, final boolean isSet) {
        if (isSet) {
            getElement().addClassName(className);
        } else {
            getElement().removeClassName(className);
        }
    }

    @Override
    public PipelineElement getItem() {
        return pipelineElement;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineElementBox that = (PipelineElementBox) o;
        return Objects.equals(pipelineElement, that.pipelineElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pipelineElement);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<HTMLPanel, PipelineElementBox> {

    }
}

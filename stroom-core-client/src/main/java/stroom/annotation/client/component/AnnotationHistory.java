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

package stroom.annotation.client.component;

import stroom.annotation.client.DurationLabel;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationTable;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.Rect;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// An annotation's history (annotation-history.css): its entries, oldest first, with runs of like
/// changes (e.g. labels added) grouped under a header that a press expands or collapses. Event and
/// annotation references are links the keyboard reaches and follows with Enter, as a press does,
/// and each entry has an actions button (Enter, Space, Shift+F10 or the context menu key, or a press
/// on the entry) that asks for the entry's menu. It shows what it is given; its owner follows the
/// links and shows the menus.
public class AnnotationHistory extends Html {

    private static final Templates TEMPLATES = GWT.create(Templates.class);

    private static final String EMPTY_VALUE = "'  '";
    private static final String ENTRY_ID_ATTRIBUTE = "entryId";
    private static final String ENTRY_TYPE_ATTRIBUTE = "entryType";
    private static final String GROUP_ID_ATTRIBUTE = "groupId";
    // The class of an entry's actions button, which opens the entry's menu
    private static final String ENTRY_ACTIONS_CLASS = "annotation-history__actions";
    private static final String ARIA_LABEL_ATTRIBUTE = "aria-label";

    private static final SafeHtml ELLIPSES = SvgImageUtil.toSafeHtml(SvgImage.ELLIPSES_VERTICAL,
            "annotation-history__ellipsis", "icon-colour__grey", "icon-wrap");
    private static final SafeHtml HISTORY_LINE = SafeHtmlUtils.fromSafeConstant(
            "<div class=\"annotation-history__line\"></div>");
    private static final SafeHtml EXPAND = SvgImageUtil.toSafeHtml(SvgImage.ARROW_DOWN,
            "annotation-history__expander", "icon-colour__grey", "icon-wrap");
    private static final SafeHtml COLLAPSE = SvgImageUtil.toSafeHtml(SvgImage.ARROW_UP,
            "annotation-history__expander", "icon-colour__grey", "icon-wrap");

    private static final Attribute HISTORY_INNER = Attribute.className("annotation-history");
    private static final Attribute HISTORY_COMMENT_BORDER = Attribute.className("annotation-history__comment");
    private static final Attribute HISTORY_COMMENT_HEADER = Attribute.className("annotation-history__comment-header");
    private static final Attribute HISTORY_COMMENT_BODY = Attribute.className("annotation-history__comment-body");
    private static final Attribute HISTORY_GROUP_HEADER = Attribute
            .className("annotation-history__comment-header annotation-history__group-header");
    private static final Attribute HISTORY_GROUP_BODY = Attribute.className("annotation-history__group-body");
    private static final Attribute HISTORY_LABEL = Attribute.className("annotation-history__label");
    private static final Attribute HISTORY_ITEM = Attribute.className("annotation-history__item");
    private static final Attribute ANNOTATION_TABLE = Attribute.className("annotation-history__table");

    // The groups expanded, by the id of their first entry
    private final Set<Long> expandedItems = new HashSet<>();

    private DurationLabel durationLabel;
    private boolean readOnly;
    private Consumer<EventId> onOpenEvent = eventId -> {
    };
    private Consumer<Long> onOpenAnnotation = annotationId -> {
    };
    private Runnable onExpandedChange = () -> {
    };
    private EntryMenuHandler onEntryMenu = (id, entryType, name, opener, popupPosition) -> {
    };

    /// Makes an empty history.
    public AnnotationHistory() {
        addDomHandler(this::onKeyDown, KeyDownEvent.getType());
        addMouseDownHandler(this::onMouseDown);
    }

    /// @param durationLabel Writes when each entry was made, relative to now, with the full time as
    ///                      its tooltip.
    public void setDurationLabel(final DurationLabel durationLabel) {
        this.durationLabel = durationLabel;
    }

    /// Shows the history.
    ///
    /// @param entries  The entries, oldest first.
    /// @param nowMs    The time now, that the entries' times are relative to.
    /// @param readOnly Whether the entries can't be changed, so show a hint in place of each
    ///                 actions button.
    public void setEntries(final List<AnnotationEntry> entries, final long nowMs, final boolean readOnly) {
        this.readOnly = readOnly;
        final HtmlBuilder html = new HtmlBuilder();

        // Group annotation entries.
        final List<AnnotationEntryGroup> groups = new ArrayList<>();
        List<AnnotationEntry> groupEntries = new ArrayList<>();
        AnnotationEntryType lastType = null;

        for (final AnnotationEntry entry : entries) {
            final Set<AnnotationEntryType> types = AnnotationEntryType.GROUPED_TYPES.get(entry.getEntryType());
            if (lastType != null && types != null && types.contains(lastType)) {
                groupEntries.add(entry);
            } else {
                groupEntries = new ArrayList<>();
                groupEntries.add(entry);
                groups.add(new AnnotationEntryGroup(entry.getId(), entry.getEntryType(), groupEntries));
            }
            lastType = entry.getEntryType();
        }

        html.div(history -> {
            SafeHtml line = SafeHtmlUtils.EMPTY_SAFE_HTML;
            boolean first = true;

            for (final AnnotationEntryGroup annotationEntryGroup : groups) {
                if (!annotationEntryGroup.getEntries().isEmpty()) {
                    if (annotationEntryGroup.getEntries().size() == 1) {
                        for (final AnnotationEntry entry : annotationEntryGroup.getEntries()) {
                            final boolean added = addEntryHtml(history, entry, nowMs, line);
                            if (added && first) {
                                // If we actually added some content then make sure we add a line marker before any
                                // subsequent content.
                                first = false;
                                line = HISTORY_LINE;
                            }
                        }
                    } else {
                        final boolean added = addGroupHtml(history, annotationEntryGroup, nowMs, line);
                        if (added && first) {
                            // If we actually added some content then make sure we add a line marker before any
                            // subsequent content.
                            first = false;
                            line = HISTORY_LINE;
                        }
                    }
                }
            }
        }, HISTORY_INNER);

        setHTML(html.toSafeHtml());
    }

    /// @param onOpenEvent Given an event reference that was followed.
    public void setOnOpenEvent(final Consumer<EventId> onOpenEvent) {
        this.onOpenEvent = Objects.requireNonNull(onOpenEvent, "onOpenEvent");
    }

    /// @param onOpenAnnotation Given the id of an annotation reference that was followed.
    public void setOnOpenAnnotation(final Consumer<Long> onOpenAnnotation) {
        this.onOpenAnnotation = Objects.requireNonNull(onOpenAnnotation, "onOpenAnnotation");
    }

    /// @param onExpandedChange Run when a group is expanded or collapsed, e.g. to fetch the history
    ///                         again, which then shows it so.
    public void setOnExpandedChange(final Runnable onExpandedChange) {
        this.onExpandedChange = Objects.requireNonNull(onExpandedChange, "onExpandedChange");
    }

    /// @param onEntryMenu Asked to show an entry's menu.
    public void setOnEntryMenu(final EntryMenuHandler onEntryMenu) {
        this.onEntryMenu = Objects.requireNonNull(onEntryMenu, "onEntryMenu");
    }

    // Enter on an event or annotation reference follows it, as a mouse press does. Enter, Space,
    // Shift+F10 or the context menu key on an entry's actions button opens the entry's menu, as
    // pressing the entry does
    private void onKeyDown(final KeyDownEvent e) {
        final Element target = e.getNativeEvent().getEventTarget().cast();
        if (e.getNativeKeyCode() == KeyCodes.KEY_ENTER && target.hasTagName("u")) {
            e.preventDefault();
            followLink(target);
        } else if (ElementUtil.hasClassName(target, ENTRY_ACTIONS_CLASS) && isMenuKey(e)) {
            e.preventDefault();
            showEntryMenu(target, new PopupPosition(
                    new Rect(target).grow(3), PopupLocation.BELOW));
        }
    }

    // A press on a link follows it; on a group, expands or collapses it; on an entry, opens its menu
    private void onMouseDown(final MouseDownEvent e) {
        // If the user has clicked on a link then consume the event.
        final Element target = e.getNativeEvent().getEventTarget().cast();
        if (target.hasTagName("u")) {
            followLink(target);
        } else {
            Element parent = target;
            String id = null;
            String type = null;
            String groupId = null;

            Element entryElement = null;
            while (parent != null && NullSafe.isBlankString(type)) {
                entryElement = parent;
                type = entryElement.getAttribute(ENTRY_TYPE_ATTRIBUTE);
                parent = parent.getParentElement();
            }

            if (entryElement != null) {
                id = entryElement.getAttribute(ENTRY_ID_ATTRIBUTE);
                groupId = entryElement.getAttribute(GROUP_ID_ATTRIBUTE);
            }

            if (NullSafe.isNonBlankString(groupId)) {
                final long gid = Long.parseLong(groupId);
                if (expandedItems.contains(gid)) {
                    expandedItems.remove(gid);
                } else {
                    expandedItems.add(gid);
                }
                onExpandedChange.run();
            } else if (NullSafe.isNonBlankString(id) && NullSafe.isNonBlankString(type)) {
                showEntryMenu(target, new PopupPosition(e.getClientX(), e.getClientY()));
            }
        }
    }

    // Whether a key opens an entry's menu from its actions button: Enter or Space, as for any
    // button, or Shift+F10 or the context menu key, as for any context menu
    private static boolean isMenuKey(final KeyDownEvent event) {
        final int keyCode = event.getNativeKeyCode();
        return keyCode == KeyCodes.KEY_ENTER
               || keyCode == KeyCodes.KEY_SPACE
               || keyCode == KeyCodes.KEY_CONTEXT_MENU
               || (keyCode == KeyCodes.KEY_F10 && event.isShiftKeyDown());
    }

    // Asks for the menu of the history entry that an element (the entry, or something in it) is in
    private void showEntryMenu(final Element element, final PopupPosition popupPosition) {
        Element entryElement = element;
        while (entryElement != null && NullSafe.isBlankString(entryElement.getAttribute(ENTRY_TYPE_ATTRIBUTE))) {
            entryElement = entryElement.getParentElement();
        }
        if (entryElement == null || NullSafe.isBlankString(entryElement.getAttribute(ENTRY_ID_ATTRIBUTE))) {
            return;
        }
        final long id = Long.parseLong(entryElement.getAttribute(ENTRY_ID_ATTRIBUTE));
        final AnnotationEntryType entryType = AnnotationEntryType.PRIMITIVE_VALUE_CONVERTER
                .fromPrimitiveValue(Byte.parseByte(entryElement.getAttribute(ENTRY_TYPE_ATTRIBUTE)));
        // The entry's actions button names the menu, and is what the menu was opened from
        final Element actionsButton = ElementUtil.findChild(entryElement, ENTRY_ACTIONS_CLASS);
        final String name = actionsButton != null
                ? actionsButton.getAttribute(ARIA_LABEL_ATTRIBUTE)
                : "Actions for history entry '" + entryType.getDisplayValue() + "'";
        onEntryMenu.show(id, entryType, name, actionsButton, popupPosition);
    }

    // Follows an event or annotation reference
    private void followLink(final Element target) {
        final String eventIdString = target.getAttribute("eventId");
        if (NullSafe.isNonBlankString(eventIdString)) {
            onOpenEvent.accept(EventId.parse(eventIdString));
        } else {
            final String annotationIdString = target.getAttribute("annotationId");
            if (NullSafe.isNonBlankString(annotationIdString)) {
                onOpenAnnotation.accept(Long.parseLong(annotationIdString));
            }
        }
    }

    private static String getUserName(final UserRef userRef) {
        if (userRef == null) {
            return "Unknown";
        }
        return userRef.toDisplayString();
    }

    private SafeHtml getEntryActions(final AnnotationEntry entry) {
        if (readOnly) {
            return ELLIPSES;
        }
        final String name = "Actions for history entry '" + entry.getEntryType().getDisplayValue()
                            + "' by " + getUserName(entry.getEntryUser());
        return TEMPLATES.actions(name,
                SafeHtmlUtil.getSafeHtmlFromSafeConstant(SvgImage.ELLIPSES_VERTICAL.getSvg()));
    }

    private Attribute[] getEntryIdAttributes(final Attribute className,
                                             final AnnotationEntry entry) {
        return new Attribute[]{
                className,
                new Attribute(SafeHtmlUtil.from(ENTRY_ID_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getId())),
                new Attribute(SafeHtmlUtil.from(ENTRY_TYPE_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getEntryType().getPrimitiveValue()))
        };
    }

    private Attribute[] getGroupIdAttributes(final Attribute className,
                                             final AnnotationEntry entry) {
        return new Attribute[]{
                className,
                new Attribute(SafeHtmlUtil.from(GROUP_ID_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getId())),
                new Attribute(SafeHtmlUtil.from(ENTRY_TYPE_ATTRIBUTE),
                        SafeHtmlUtil.from(entry.getEntryType().getPrimitiveValue()))
        };
    }

    private boolean addGroupHtml(final HtmlBuilder html,
                                 final AnnotationEntryGroup group,
                                 final long nowMs,
                                 final SafeHtml line) {
        @SuppressWarnings("SequencedCollectionMethodCanBeUsed")
        final AnnotationEntry first = group.getEntries().get(0);
        final boolean expanded = expandedItems.contains(first.getId());
        final AnnotationEntryType entryType = first.getEntryType();
        UserRef user = first.getEntryUser();
        for (final AnnotationEntry entry : group.getEntries()) {
            if (!Objects.equals(entry.getEntryUser(), user)) {
                user = null;
                break;
            }
        }
        final UserRef userRef = user;
        final String actionText = getActionText(group, entryType);

        html.append(line);
        html.div(border -> {
            border.div(header -> {
                // Add row icon.
                addIcon(header, first.getEntryType());

                // Add row label.
                header.div(label -> {
                    if (userRef != null) {
                        label.bold(getUserName(userRef));
                        label.nbsp();
                    }
                    label.append(actionText);
                    label.nbsp();
                    durationLabel.append(label, first.getEntryTime(), nowMs);
                }, HISTORY_LABEL);

                // Add expander icon.
                if (expanded) {
                    header.append(COLLAPSE);
                } else {
                    header.append(EXPAND);
                }
            }, getGroupIdAttributes(HISTORY_GROUP_HEADER, first));

            // Add content if expanded.
            if (expanded) {
                border.div(body -> {
                    for (final AnnotationEntry entry : group.getEntries()) {
                        addEntryHtml(body, entry, nowMs, line);
                    }
                }, HISTORY_GROUP_BODY);
            }
        }, HISTORY_COMMENT_BORDER);

        return true;
    }

    private static String getActionText(final AnnotationEntryGroup group, final AnnotationEntryType entryType) {
        final int count = group.getEntries().size();
        return switch (entryType) {
            case TITLE,
                 SUBJECT,
                 STATUS,
                 ASSIGNED,
                 COMMENT,
                 RETENTION_PERIOD,
                 DESCRIPTION,
                 DELETE -> entryType.getActionText();
            case ADD_TABLE_DATA -> "added table data";
            case LINK_EVENT, UNLINK_EVENT -> "changed " + count + " linked events";
            case ADD_TO_COLLECTION, REMOVE_FROM_COLLECTION -> "changed " + count + " collections";
            case ADD_LABEL, REMOVE_LABEL -> "changed " + count + " labels";
            case LINK_ANNOTATION, UNLINK_ANNOTATION -> "changed " + count + " linked annotations";
        };
    }

    private boolean addEntryHtml(final HtmlBuilder html,
                                 final AnnotationEntry entry,
                                 final long nowMs,
                                 final SafeHtml line) {
        boolean added = false;
        final String entryUiValue = NullSafe.get(entry.getEntryValue(), EntryValue::asUiValue);

        switch (entry.getEntryType()) {
            case COMMENT -> {
                html.append(line);
                html.div(border -> {
                    border.div(header -> {
                        // Add row label.
                        header.div(label -> {
                            label.bold(getUserName(entry.getEntryUser()));
                            label.append(HtmlBuilder.NB_SPACE);
                            label.appendTrustedString("commented");
                            label.append(HtmlBuilder.NB_SPACE);
                            durationLabel.append(label, entry.getEntryTime(), nowMs);

                            if (!Objects.equals(entry.getEntryUser(), entry.getUpdateUser()) ||
                                !Objects.equals(entry.getEntryTime(), entry.getUpdateTime())) {
                                label.append(HtmlBuilder.NB_SPACE);
                                label.appendTrustedString(" - ");
                                label.append(HtmlBuilder.NB_SPACE);
                                label.bold(getUserName(entry.getUpdateUser()));
                                label.append(HtmlBuilder.NB_SPACE);
                                label.appendTrustedString("edited");
                                label.append(HtmlBuilder.NB_SPACE);
                                durationLabel.append(label, entry.getUpdateTime(), nowMs);
                            }
                        }, HISTORY_LABEL);

                        // Add change icon.
                        header.append(getEntryActions(entry));

                    }, getEntryIdAttributes(HISTORY_COMMENT_HEADER, entry));
                    border.div(body -> CommentDecoration.decorateComment(body, entryUiValue), HISTORY_COMMENT_BODY);
                }, HISTORY_COMMENT_BORDER);
                added = true;
            }
            case LINK_EVENT,
                 UNLINK_EVENT,
                 LINK_ANNOTATION,
                 UNLINK_ANNOTATION,
                 ADD_TO_COLLECTION,
                 REMOVE_FROM_COLLECTION,
                 ADD_LABEL,
                 REMOVE_LABEL,
                 DELETE -> {
                html.append(line);
                html.div(item -> {
                    // Add row icon.
                    addIcon(item, entry.getEntryType());

                    // Add row label.
                    item.div(label -> {
                        label.bold(getUserName(entry.getEntryUser()));
                        label.nbsp();
                        label.append(entry.getEntryType().getActionText());
                        label.nbsp();
                        CommentDecoration.link(html, entry.getEntryType(), entryUiValue);
                        label.nbsp();
                        durationLabel.append(label, entry.getEntryTime(), nowMs);
                    }, HISTORY_LABEL);

                    // Add change icon.
                    item.append(getEntryActions(entry));
                }, getEntryIdAttributes(HISTORY_ITEM, entry));

                added = true;
            }
            case ADD_TABLE_DATA -> {
                if (entry.getEntryValue() instanceof final AnnotationTable table) {
                    final List<List<String>> values = NullSafe.list(table.getValues());

                    html.append(line);
                    html.div(border -> {
                        border.div(header -> {
                            // Add row icon.
                            addIcon(header, entry.getEntryType());

                            // Add row label.
                            header.div(label -> {
                                label.bold(getUserName(entry.getEntryUser()));
                                label.nbsp();
                                label.append(values.size() == 1
                                        ? "added " + values.size() + " row"
                                        : "added " + values.size() + " rows");
                                label.nbsp();
                                durationLabel.append(label, entry.getEntryTime(), nowMs);
                            }, HISTORY_LABEL);

                            // Add change icon.
                            header.append(getEntryActions(entry));

                        }, getEntryIdAttributes(HISTORY_COMMENT_HEADER, entry));

                        border.div(body -> {
                            body.table(tb -> {
                                // Add table header
                                tb.tr(tr -> {
                                    for (final String col : NullSafe.list(table.getColumns())) {
                                        tr.th(col);
                                    }
                                });

                                // Add table body
                                for (final List<String> row : values) {
                                    tb.tr(tr -> {
                                        for (final String val : NullSafe.list(row)) {
                                            tr.td(val);
                                        }
                                    });
                                }
                            }, ANNOTATION_TABLE);
                        }, HISTORY_COMMENT_BODY);
                    }, HISTORY_COMMENT_BORDER);

                    added = true;
                }
            }
            case ASSIGNED -> {
                if (entry.getPreviousValue() != null) {
                    html.append(line);
                    html.div(item -> {
                        // Add row icon.
                        addIcon(item, entry.getEntryType());

                        // Add row label.
                        item.div(label -> {
                            label.bold(getUserName(entry.getEntryUser()));
                            if (areSameUser(entry.getEntryUser(), entry.getPreviousValue())) {
                                label.nbsp();
                                label.appendTrustedString("removed their assignment");
                            } else {
                                label.nbsp();
                                label.appendTrustedString("unassigned");
                                label.nbsp();
                                label.bold(getValueString(entry.getPreviousValue().asUiValue()));
                            }
                            label.nbsp();
                            durationLabel.append(label, entry.getEntryTime(), nowMs);
                        }, HISTORY_LABEL);

                        // Add change icon.
                        item.append(getEntryActions(entry));
                    }, getEntryIdAttributes(HISTORY_ITEM, entry));
                    added = true;
                }

                if (entryUiValue != null && !entryUiValue.trim().isEmpty()) {
                    html.append(line);
                    html.div(item -> {
                        // Add row icon.
                        addIcon(html, entry.getEntryType());

                        // Add row label.
                        item.div(label -> {
                            label.bold(getUserName(entry.getEntryUser()));
                            if (areSameUser(entry.getEntryUser(), entry.getEntryValue())) {
                                label.nbsp();
                                label.appendTrustedString("self-assigned this");
                            } else {
                                label.nbsp();
                                label.appendTrustedString("assigned");
                                label.nbsp();
                                label.bold(getValueString(entryUiValue));
                            }
                            label.nbsp();
                            durationLabel.append(label, entry.getEntryTime(), nowMs);
                        }, HISTORY_LABEL);

                        // Add change icon.
                        item.append(getEntryActions(entry));
                    }, getEntryIdAttributes(HISTORY_ITEM, entry));
                    added = true;
                }
            }
            default -> {
                html.append(line);
                html.div(item -> {
                    // Add row icon.
                    addIcon(html, entry.getEntryType());

                    // Add row label.
                    item.div(label -> {
                        label.bold(getUserName(entry.getEntryUser()));
                        if (entry.getPreviousValue() != null) {
                            label.nbsp();
                            label.appendTrustedString("changed the");
                            label.nbsp();
                        } else {
                            label.nbsp();
                            label.appendTrustedString("set the");
                            label.nbsp();
                        }
                        label.append(entry.getEntryType().getDisplayValue().toLowerCase());
                        label.nbsp();

                        if (entry.getPreviousValue() != null) {
                            label.del(getValueString(entry.getPreviousValue().asUiValue()));
                            label.nbsp();
                            label.appendTrustedString("to");
                            label.nbsp();
                            label.ins(getValueString(entryUiValue));

                        } else {
                            label.append(getValueString(entryUiValue));
                        }

                        label.nbsp();
                        durationLabel.append(label, entry.getEntryTime(), nowMs);
                    }, HISTORY_LABEL);

                    // Add change icon.
                    item.append(getEntryActions(entry));
                }, getEntryIdAttributes(HISTORY_ITEM, entry));
                added = true;
            }
        }
        return added;
    }

    private void addIcon(final HtmlBuilder html,
                         final AnnotationEntryType annotationEntryType) {
        final SvgImage image = switch (annotationEntryType) {
            case TITLE -> SvgImage.EDIT;
            case SUBJECT -> SvgImage.EDIT;
            case STATUS -> SvgImage.EXCLAMATION;
            case ASSIGNED -> SvgImage.USER;
            case COMMENT -> SvgImage.EDIT;
            case LINK_EVENT -> SvgImage.LINK;
            case UNLINK_EVENT -> SvgImage.UNLINK;
            case RETENTION_PERIOD -> SvgImage.CALENDAR;
            case DESCRIPTION -> SvgImage.EDIT;
            case ADD_TO_COLLECTION -> SvgImage.TABLE_NESTED;
            case REMOVE_FROM_COLLECTION -> SvgImage.TABLE_NESTED;
            case ADD_LABEL -> SvgImage.TAGS;
            case REMOVE_LABEL -> SvgImage.TAGS;
            case ADD_TABLE_DATA -> SvgImage.TABLE;
            case LINK_ANNOTATION -> SvgImage.LINK;
            case UNLINK_ANNOTATION -> SvgImage.UNLINK;
            case DELETE -> SvgImage.CLEAR;
        };
        html.append(SafeHtmlUtil.getSafeHtmlFromSafeConstant(image.getSvg()));
    }

    private static boolean areSameUser(final UserRef entryUser, final EntryValue entryValue) {
        if (entryValue instanceof final UserRefEntryValue userRefEntryValue) {
            return Objects.equals(entryUser, userRefEntryValue.getUserRef());
        } else {
            return entryUser == null && entryValue == null;
        }
    }

    private static String getValueString(final String string) {
        if (string != null && !string.trim().isEmpty()) {
            return string;
        }
        return EMPTY_VALUE;
    }


    // --------------------------------------------------------------------------------


    /// Shows the menu of a history entry.
    @FunctionalInterface
    public interface EntryMenuHandler {

        /// @param id            The entry's id.
        /// @param entryType     The entry's type.
        /// @param name          The menu's name, for screen readers.
        /// @param opener        The entry's actions button, that the menu is opened from, or null if
        ///                      it has none.
        /// @param popupPosition Where to show the menu.
        void show(long id, AnnotationEntryType entryType, String name, Element opener, PopupPosition popupPosition);
    }


    // --------------------------------------------------------------------------------


    // An entry's actions button: its icon, named for the entry
    interface Templates extends SafeHtmlTemplates {

        @Template("<div class=\"annotation-history__ellipsis icon-colour__grey icon-wrap annotation-history__actions\" "
                  + "role=\"button\" tabindex=\"0\" aria-haspopup=\"menu\" aria-expanded=\"false\" "
                  + "aria-label=\"{0}\">{1}</div>")
        SafeHtml actions(String name, SafeHtml icon);
    }


    // --------------------------------------------------------------------------------


    @SuppressWarnings("ClassCanBeRecord") // GWT moans if this is a record
    private static final class AnnotationEntryGroup {

        private final long id;
        private final AnnotationEntryType annotationEntryType;
        private final List<AnnotationEntry> entries;

        private AnnotationEntryGroup(final long id,
                                     final AnnotationEntryType annotationEntryType,
                                     final List<AnnotationEntry> entries) {
            this.id = id;
            this.annotationEntryType = annotationEntryType;
            this.entries = entries;
        }

        public long getId() {
            return id;
        }

        public AnnotationEntryType getAnnotationEntryType() {
            return annotationEntryType;
        }

        public List<AnnotationEntry> getEntries() {
            return entries;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            final AnnotationEntryGroup that = (AnnotationEntryGroup) obj;
            return this.id == that.id &&
                   Objects.equals(this.annotationEntryType, that.annotationEntryType) &&
                   Objects.equals(this.entries, that.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, annotationEntryType, entries);
        }

        @Override
        public String toString() {
            return "AnnotationEntryGroup[" +
                   "id=" + id + ", " +
                   "annotationEntryType=" + annotationEntryType + ", " +
                   "entries=" + entries + ']';
        }
    }
}

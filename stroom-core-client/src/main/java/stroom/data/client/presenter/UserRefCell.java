package stroom.data.client.presenter;

import stroom.data.client.presenter.UserRefCell.UserRefProvider;
import stroom.data.grid.client.EventCell;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserEvent;
import stroom.security.shared.AppPermission;
import stroom.svg.client.IconColour;
import stroom.svg.client.Preset;
import stroom.svg.shared.SvgImage;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.util.client.ElementUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Function;

import static com.google.gwt.dom.client.BrowserEvents.MOUSEDOWN;

public class UserRefCell<T_ROW> extends AbstractCell<UserRefProvider<T_ROW>>
        implements HasHandlers, EventCell {

    private static final String ICON_CLASS_NAME = "svgIcon";
    private static final String COPY_CLASS_NAME = "userRefLinkCopy";
    private static final String OPEN_CLASS_NAME = "userRefLinkOpen";
    private static final String HOVER_ICON_CLASS_NAME = "hoverIcon";
    private static final String HOVER_ICON_CONTAINER_CLASS_NAME = "hoverIconContainer";

    private final EventBus eventBus;
    private final ClientSecurityContext securityContext;
    private final boolean showIcon;
    private final UserRef.DisplayType displayType;
    private final Function<T_ROW, String> cssClassFunction;

    private static volatile Template template;

    public UserRefCell(final EventBus eventBus,
                       final ClientSecurityContext securityContext,
                       final UserRef.DisplayType displayType) {
        this(eventBus, securityContext, false, displayType, null);
    }

    public UserRefCell(final EventBus eventBus,
                       final ClientSecurityContext securityContext,
                       final boolean showIcon,
                       final UserRef.DisplayType displayType,
                       final Function<T_ROW, String> cssClassFunction) {
        super(MOUSEDOWN);
        this.eventBus = eventBus;
        this.securityContext = securityContext;
        this.showIcon = showIcon;
        this.displayType = displayType;
        this.cssClassFunction = cssClassFunction;
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public boolean isConsumed(final CellPreviewEvent<?> event) {
        final NativeEvent nativeEvent = event.getNativeEvent();
        if (MOUSEDOWN.equals(nativeEvent.getType()) && MouseUtil.isPrimary(nativeEvent)) {
            final Element element = nativeEvent.getEventTarget().cast();
            return ElementUtil.hasClassName(element, COPY_CLASS_NAME, 0, 5) ||
                   ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 0, 5);
        }
        return false;
    }

    @Override
    public void onBrowserEvent(final Context context,
                               final Element parent,
                               final UserRefProvider<T_ROW> value,
                               final NativeEvent event,
                               final ValueUpdater<UserRefProvider<T_ROW>> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (value.getUserRef() != null) {
            if (MOUSEDOWN.equals(event.getType()) && MouseUtil.isPrimary(event)) {
                onEnterKeyDown(context, parent, value, event, valueUpdater);
            }
        }
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }

    @Override
    protected void onEnterKeyDown(final Context context,
                                  final Element parent,
                                  final UserRefProvider<T_ROW> value,
                                  final NativeEvent event,
                                  final ValueUpdater<UserRefProvider<T_ROW>> valueUpdater) {
        final Element element = event.getEventTarget().cast();
        final UserRef userRef = GwtNullSafe.get(value, UserRefProvider::getUserRef);
        if (userRef != null) {
            if (ElementUtil.hasClassName(element, COPY_CLASS_NAME, 0, 5)) {
                final String text = userRef.toDisplayString(displayType);
                if (text != null) {
                    ClipboardUtil.copy(text);
                }
            } else if (ElementUtil.hasClassName(element, OPEN_CLASS_NAME, 0, 5)) {
                OpenUserEvent.fire(this, userRef);
            }
        }
    }

    @Override
    public void render(final Context context,
                       final UserRefProvider<T_ROW> value,
                       final SafeHtmlBuilder sb) {
        final UserRef userRef = GwtNullSafe.get(value, UserRefProvider::getUserRef);

        if (userRef == null) {
            sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
        } else {
            final String cellPlainText = GwtNullSafe.getOrElse(
                    userRef,
                    ref -> ref.toDisplayString(displayType),
                    "");
            if (GwtNullSafe.isBlankString(cellPlainText)) {
                sb.append(SafeHtmlUtils.EMPTY_SAFE_HTML);
            } else {
                final SafeHtml cellHtmlText = SafeHtmlUtils.fromString(cellPlainText);
                String cssClasses = "userRefLinkText";
                if (cssClassFunction != null) {
                    final String additionalClasses = cssClassFunction.apply(value.getRow());
                    if (additionalClasses != null) {
                        cssClasses += " " + additionalClasses;
                    }
                }
                final SafeHtml textDiv = template.div(cssClasses, cellHtmlText);

                final String containerClasses = String.join(
                        " ",
                        HOVER_ICON_CONTAINER_CLASS_NAME,
                        "userRefLinkContainer");
                sb.appendHtmlConstant("<div class=\"" + containerClasses + "\">");

                if (showIcon) {
                    final String title = userRef.getType(CaseType.SENTENCE) + " " + cellPlainText;
                    final Preset icon = userRef.isGroup()
                            ? UserTabPlugin.GROUP_ICON
                            : UserTabPlugin.USER_ICON;
                    final SafeHtml iconDiv = SvgImageUtil.toSafeHtml(
                            title,
                            icon.getSvgImage(),
                            ICON_CLASS_NAME,
                            IconColour.BLUE.getClassName(),
                            "userRefLinkIcon");
                    sb.append(iconDiv);
                }
                sb.append(textDiv);

                final SafeHtml copySvg = SvgImageUtil.toSafeHtml(
                        SvgImage.COPY, ICON_CLASS_NAME, COPY_CLASS_NAME, HOVER_ICON_CLASS_NAME);
                sb.append(template.divWithToolTip(
                        "Copy " + displayType.getTypeName() + " '" + cellPlainText + "' to clipboard",
                        copySvg));

                // Not all users should be able to open the user
                if (hasPermissionToOpen(userRef)) {
                    final SafeHtml openSvg = SvgImageUtil.toSafeHtml(
                            SvgImage.OPEN, ICON_CLASS_NAME, OPEN_CLASS_NAME, HOVER_ICON_CLASS_NAME);
                    sb.append(template.divWithToolTip(
                            "Open " + userRef.getType(CaseType.LOWER) + " " + cellPlainText + " in new tab",
                            openSvg));
                }

                sb.appendHtmlConstant("</div>");
            }
        }
    }

    private boolean hasPermissionToOpen(final UserRef userRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
               || securityContext.isCurrentUser(userRef);
    }


    // --------------------------------------------------------------------------------


    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String cssClass, SafeHtml content);

        @Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithToolTip(String title, SafeHtml content);
    }


    // --------------------------------------------------------------------------------


    public static class UserRefProvider<T_ROW> {

        private final T_ROW row;
        private final Function<T_ROW, UserRef> userRefExtractor;

        public UserRefProvider(final T_ROW row,
                               final Function<T_ROW, UserRef> userRefExtractor) {
            this.row = row;
            this.userRefExtractor = Objects.requireNonNull(userRefExtractor);
        }

        /**
         * For uses where the rendering of the cell doesn't need the original row value, so
         * this essentially returns an identity function.
         */
        public static UserRefProvider<UserRef> forUserRef(final UserRef userRef) {
            return new UserRefProvider<>(userRef, Function.identity());
        }

        public T_ROW getRow() {
            return row;
        }

        public UserRef getUserRef() {
            return GwtNullSafe.get(row, userRefExtractor);
        }
    }
}

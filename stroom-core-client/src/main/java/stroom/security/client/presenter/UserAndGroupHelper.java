package stroom.security.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.security.shared.User;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Provider;

import java.util.function.Consumer;

public class UserAndGroupHelper {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    public static final String COL_NAME_DISPLAY_NAME = "Display Name";
    public static final String COL_NAME_FULL_NAME = "Full Name";
    public static final String COL_NAME_UNIQUE_USER_ID = "Unique User ID";
    public static final String COL_NAME_ENABLED = "Enabled";

    private UserAndGroupHelper() {
    }

    public static SafeHtml buildSingleIconHeader(final boolean isGroup) {
        final String iconClassName = "svgCell-icon";
        final Preset preset = isGroup
                ? SvgPresets.USER_GROUP.title("")
                : SvgPresets.USER.title("");

        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    preset.getTitle(),
                                    preset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("single-icon-column-header"))
                .toSafeHtml();
    }

    public static SafeHtml buildUserAndGroupIconHeader() {
        final String iconClassName = "svgCell-icon";
        final Preset userPreset = SvgPresets.USER.title("");
        final Preset groupPreset = SvgPresets.USER_GROUP.title("");
        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    userPreset.getTitle(),
                                    userPreset.getSvgImage(),
                                    iconClassName));
                            divBuilder.append("/");
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    groupPreset.getTitle(),
                                    groupPreset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("two-icon-column-header"))
                .toSafeHtml();
    }

    public static Preset mapUserRefTypeToIcon(final UserRef userRef) {
        // TODO get enabled state from userRef
        return GwtNullSafe.get(userRef,
                userRef2 -> mapUserTypeToIcon(userRef2.isGroup(), true));
    }

    public static Preset mapUserTypeToIcon(final User user) {
        return GwtNullSafe.get(user,
                usr -> mapUserTypeToIcon(usr.isGroup(), usr.isEnabled()));
    }

    public static Preset mapUserTypeToIcon(final boolean isGroup,
                                           final boolean isEnabled) {
        final Preset preset = isGroup
                ? SvgPresets.USER_GROUP
                : SvgPresets.USER;
        return preset.enabled(isEnabled);
    }

    public static void onDelete(final UserListPresenter userListPresenter,
                                final RestFactory restFactory,
                                final HasHandlers hasHandlers) {

        final User user = userListPresenter.getSelectionModel().getSelected();
        if (user != null) {
            ConfirmEvent.fire(
                    hasHandlers,
                    "Are you sure you want to delete the selected " +
                    getDescription(user) + "?",
                    ok -> {
                        if (ok) {
                            user.setEnabled(false);
                            restFactory
                                    .create(USER_RESOURCE)
                                    .method(res -> res.update(user))
                                    .onSuccess(createAfterChangeConsumer(userListPresenter))
                                    .taskMonitorFactory(userListPresenter.getPagerView())
                                    .exec();
                        }
                    });
        }
    }

    public static void onEditUserOrGroup(final UserListPresenter userListPresenter,
                                         final Provider<CreateNewGroupPresenter> createNewGroupPresenterProvider,
                                         final TaskMonitorFactory taskMonitorFactory) {
        final User user = userListPresenter.getSelectionModel().getSelected();
        if (user != null && user.isGroup()) {
            final CreateNewGroupPresenter createNewGroupPresenter = createNewGroupPresenterProvider.get();
            createNewGroupPresenter.getView().setName(user.getSubjectId());
            final PopupSize popupSize = PopupSize.resizable(600, 200);
            ShowPopupEvent.builder(createNewGroupPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Edit User Group")
                    .onShow(e -> createNewGroupPresenter.getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            createNewGroupPresenter.editGroupName(
                                    user,
                                    createAfterChangeConsumer(userListPresenter),
                                    e,
                                    taskMonitorFactory);
                        } else {
                            e.hide();
                        }
                    })
                    .fire();
        }
    }

    public static Consumer<User> createAfterChangeConsumer(final UserListPresenter userListPresenter) {
        return user -> {
            userListPresenter.getSelectionModel()
                    .clear(true);
            userListPresenter.refresh();
        };
    }

    public static String getDescription(final User user) {
        return user.isGroup()
                ? "group '" + user.asRef().toDisplayString() + "'"
                : "user '" + user.asRef().toDisplayString() + "'";
    }
}

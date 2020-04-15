import * as React from "react";
import { SubMenuProps } from "../types";
import { MenuItemType } from "../../MenuItem/types";

const useAdmin = ({
  navigateApp: {
    nav: { goToUserSettings, goToAuthorisationManager, goToUsers, goToApiKeys },
  },
  menuItemToggled,
}: SubMenuProps): MenuItemType =>
  React.useMemo(
    () => ({
      key: "admin",
      title: "Admin",
      onClick: () => menuItemToggled("admin"),
      icon: "cogs",
      style: "nav",
      skipInContractedMenu: true,
      children: [
        {
          key: "userSettings",
          title: "Me",
          onClick: goToUserSettings,
          icon: "user",
          style: "nav",
        },
        {
          key: "adminPermissions",
          title: "Permissions",
          icon: "key",
          style: "nav",
          onClick: () => menuItemToggled("adminPermissions"),
          children: [true, false].map((isGroup: boolean) => ({
            key: `${isGroup ? "groupPermissions" : "userPermissions"}`,
            title: isGroup ? "Group" : "User",
            onClick: () => goToAuthorisationManager(isGroup.toString()),
            icon: "user",
            style: "nav",
          })),
        },
        {
          key: "userIdentities",
          title: "Users",
          onClick: goToUsers,
          icon: "users",
          style: "nav",
        },
        {
          key: "apiKeys",
          title: "API Keys",
          onClick: goToApiKeys,
          icon: "key",
          style: "nav",
        },
      ],
    }),
    [
      menuItemToggled,
      goToUserSettings,
      goToAuthorisationManager,
      goToUsers,
      goToApiKeys,
    ],
  );

export default useAdmin;

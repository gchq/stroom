import * as React from "react";
import { SubMenuProps } from "../types";
import { MenuItemType } from "../../MenuItem/types";

export const useWelcomeMenu = ({
  navigateApp: {
    nav: { goToWelcome },
  },
}: SubMenuProps): MenuItemType =>
  React.useMemo(
    () => ({
      key: "welcome",
      title: "Welcome",
      onClick: goToWelcome,
      icon: "home",
      style: "nav",
    }),
    [goToWelcome],
  );

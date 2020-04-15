import * as React from "react";
import { SubMenuProps } from "../types";
import { MenuItemType } from "../../MenuItem/types";

const useDataViewerMenu = ({
  navigateApp: {
    nav: { goToStreamBrowser },
  },
}: SubMenuProps): MenuItemType =>
  React.useMemo(
    () => ({
      key: "data",
      title: "Data",
      onClick: goToStreamBrowser,
      icon: "database",
      style: "nav",
    }),
    [goToStreamBrowser],
  );

export default useDataViewerMenu;

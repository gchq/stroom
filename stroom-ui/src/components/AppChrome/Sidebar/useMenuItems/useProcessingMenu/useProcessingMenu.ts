import * as React from "react";
import { SubMenuProps } from "../types";
import { MenuItemType } from "../../MenuItem/types";

const useProcessingMenu = ({
  navigateApp: {
    nav: { goToProcessing },
  },
}: SubMenuProps): MenuItemType =>
  React.useMemo(
    () => ({
      key: "processing",
      title: "Processing",
      onClick: goToProcessing,
      icon: "play",
      style: "nav",
    }),
    [goToProcessing],
  );

export default useProcessingMenu;

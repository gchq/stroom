import * as React from "react";
import { SubMenuProps } from "../types";
import { MenuItemType } from "../../MenuItem/types";

const useIndexingMenu = ({
  navigateApp: {
    nav: { goToIndexVolumes, goToIndexVolumeGroups },
  },
  menuItemToggled,
}: SubMenuProps): MenuItemType =>
  React.useMemo(
    () => ({
      key: "indexing",
      title: "Indexing",
      onClick: () => menuItemToggled("indexing"),
      icon: "database",
      style: "nav",
      skipInContractedMenu: true,
      children: [
        {
          key: "indexVolumes",
          title: "Index Volumes",
          onClick: goToIndexVolumes,
          icon: "database",
          style: "nav",
        },
        {
          key: "indexVolumeGroups",
          title: "Index Groups",
          onClick: goToIndexVolumeGroups,
          icon: "database",
          style: "nav",
        },
      ],
    }),
    [menuItemToggled, goToIndexVolumeGroups, goToIndexVolumes],
  );

export default useIndexingMenu;

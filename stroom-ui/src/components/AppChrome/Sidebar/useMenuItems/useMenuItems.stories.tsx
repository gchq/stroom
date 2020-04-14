import * as React from "react";
import { storiesOf } from "@storybook/react";

import useMenuItems from "./useMenuItems";
import JsonDebug from "testing/JsonDebug";
import {
  MenuItemType,
  MenuItemsOpenState,
  MenuItemToggled,
} from "../MenuItem/types";

interface MenuItemListProps {
  menuItems: MenuItemType[];
  menuItemIsOpenByKey: MenuItemsOpenState;
  menuItemToggled: MenuItemToggled;
}

const MenuItemList: React.FunctionComponent<MenuItemListProps> = ({
  menuItems,
  menuItemIsOpenByKey,
  menuItemToggled,
}) => (
  <ul>
    {menuItems.map(({ key, title, children }) => (
      <li key={key}>
        {!!children ? (
          <span onClick={() => menuItemToggled(key)}>
            {title} - {menuItemIsOpenByKey[key] ? "OPEN" : "CLOSED"}
          </span>
        ) : (
          <span>{title}</span>
        )}
        {children && menuItemIsOpenByKey[key] && (
          <MenuItemList
            {...{ menuItemIsOpenByKey, menuItemToggled }}
            menuItems={children}
          />
        )}
      </li>
    ))}
  </ul>
);

const TestHarness: React.FunctionComponent = () => {
  const {
    menuItems,
    menuItemIsOpenByKey,
    openMenuItemKeys,
    menuItemToggled,
  } = useMenuItems();

  return (
    <div>
      <p>
        Click on the title of a folder to expand it. Be aware leaf nodes will
        not be shown.
      </p>
      <MenuItemList {...{ menuItems, menuItemIsOpenByKey, menuItemToggled }} />
      <JsonDebug value={{ menuItemIsOpenByKey, openMenuItemKeys }} />
    </div>
  );
};

storiesOf("App Chrome/Sidebar/useMenuItems", module).add("test", () => (
  <TestHarness />
));

import { NavigateApp } from "lib/useAppNavigation/types";

export interface SubMenuProps {
  navigateApp: NavigateApp;
  menuItemToggled: (key: string) => void;
}

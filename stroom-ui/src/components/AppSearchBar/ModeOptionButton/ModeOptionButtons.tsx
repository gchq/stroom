import * as React from "react";

import Button from "../../Button";
import { IconProp } from "@fortawesome/fontawesome-svg-core";
import { SearchMode } from "./types";

interface Props {
  searchMode: SearchMode;
  switchMode: (m: SearchMode) => void;
}

interface ModeOption {
  mode: SearchMode;
  icon: IconProp;
  title: string;
}

const MODE_OPTIONS: ModeOption[] = [
  {
    mode: SearchMode.GLOBAL_SEARCH,
    icon: "search",
    title: "Search",
  },
  {
    mode: SearchMode.NAVIGATION,
    icon: "folder",
    title: "Navigation",
  },
  {
    mode: SearchMode.RECENT_ITEMS,
    icon: "history",
    title: "Recent Items",
  },
];

const ModeOptionButtons: React.FunctionComponent<Props> = ({ switchMode }) => (
  <React.Fragment>
    {MODE_OPTIONS.map((modeOption) => (
      <Button
        key={modeOption.mode}
        icon={modeOption.icon}
        appearance="icon"
        onClick={(e) => {
          switchMode(modeOption.mode);
          e.stopPropagation();
          e.preventDefault();
        }}
        onKeyDown={(e) => {
          if (e.key === " ") {
            switchMode(modeOption.mode);
          }
          e.stopPropagation();
        }}
      />
    ))}
  </React.Fragment>
);

interface UseModeOptionButtons {
  searchMode: SearchMode;
  componentProps: Props;
}

export const useModeOptionButtons = (): UseModeOptionButtons => {
  const [searchMode, switchMode] = React.useState<SearchMode>(
    SearchMode.NAVIGATION,
  );

  return {
    searchMode,
    componentProps: {
      searchMode,
      switchMode,
    },
  };
};

export default ModeOptionButtons;

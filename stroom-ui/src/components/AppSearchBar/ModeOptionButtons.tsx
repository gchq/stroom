import * as React from "react";

import Button from "../Button";
import { SearchMode } from "./redux";
import { IconProp } from "@fortawesome/fontawesome-svg-core";

export interface Props {
  switchMode: (m: SearchMode) => void;
}

export interface ModeOption {
  mode: SearchMode;
  icon: IconProp;
  position: "left" | "middle" | "right";
}

const MODE_OPTIONS: Array<ModeOption> = [
  {
    mode: SearchMode.GLOBAL_SEARCH,
    icon: "search",
    position: "left"
  },
  {
    mode: SearchMode.NAVIGATION,
    icon: "folder",
    position: "middle"
  },
  {
    mode: SearchMode.RECENT_ITEMS,
    icon: "history",
    position: "right"
  }
];

const ModeOptionButtons = ({ switchMode }: Props) => (
  <React.Fragment>
    {MODE_OPTIONS.map(modeOption => (
      <Button
        key={modeOption.mode}
        icon={modeOption.icon}
        groupPosition={modeOption.position}
        onClick={e => switchMode(modeOption.mode)}
        onKeyDown={e => {
          if (e.key === " ") {
            switchMode(modeOption.mode);
          }
          e.stopPropagation();
        }}
      />
    ))}
  </React.Fragment>
);

export default ModeOptionButtons;

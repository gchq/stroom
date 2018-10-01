import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";

import Button from "../Button";
import {
  actionCreators as appSearchBarActionCreators,
  SearchMode
} from "./redux";
import { GlobalStoreState } from "../../startup/reducers";
import { IconProp } from "@fortawesome/fontawesome-svg-core";

const { switchMode } = appSearchBarActionCreators;

export interface Props {
  pickerId: string;
}

interface ConnectState {}
interface ConnectDispatch {
  switchMode: typeof switchMode;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    {
      switchMode
    }
  )
);

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

const ModeOptionButtons = ({ switchMode, pickerId }: EnhancedProps) => (
  <React.Fragment>
    {MODE_OPTIONS.map(modeOption => (
      <Button
        key={modeOption.mode}
        icon={modeOption.icon}
        groupPosition={modeOption.position}
        onClick={e => switchMode(pickerId, modeOption.mode)}
        onKeyDown={e => {
          if (e.key === " ") {
            switchMode(pickerId, modeOption.mode);
          }
          e.stopPropagation();
        }}
      />
    ))}
  </React.Fragment>
);

export default enhance(ModeOptionButtons);

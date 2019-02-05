import * as React from "react";

import Select from "react-select";
import { compose, lifecycle, withProps } from "recompose";
import { connect } from "react-redux";
import { GlobalStoreState } from "src/startup/reducers";

import { getIndexVolumeGroupNames } from "./client";
import { SelectOptionType } from "src/types";

export interface Props {
  value?: string;
  onChange: (v: string) => any;
}

export interface ConnectState {
  groupNames: Array<string>;
}

export interface ConnectDispatch {
  getIndexVolumeGroupNames: typeof getIndexVolumeGroupNames;
}

export interface WithProps {
  options: Array<SelectOptionType>;
}

interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ indexVolumeGroups: { groupNames } }) => ({
      groupNames
    }),
    {
      getIndexVolumeGroupNames
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { getIndexVolumeGroupNames } = this.props;

      getIndexVolumeGroupNames();
    }
  }),
  withProps<WithProps, ConnectState>(({ groupNames }) => ({
    options: groupNames.map(n => ({
      value: n,
      label: n
    }))
  }))
);

const IndexVolumeGroupPicker = ({
  options,
  value,
  onChange
}: EnhancedProps) => (
  <Select
    value={options.find(o => o.value === value)}
    onChange={(o: SelectOptionType) => onChange(o.value)}
    placeholder="Index Volume Group"
    options={options}
  />
);

export default enhance(IndexVolumeGroupPicker);

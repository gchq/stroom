import * as React from "react";
import { useEffect } from "react";
import { compose } from "recompose";
import { connect } from "react-redux";

import Loader from "../Loader";
import { Props as ButtonProps } from "../Button";
import DocRefEditor from "../DocRefEditor";
import { fetchDictionary, saveDictionary } from "./client";
import { actionCreators, StoreStatePerId } from "./redux";
import { GlobalStoreState } from "../../startup/reducers";

const { dictionaryUpdated } = actionCreators;

export interface Props {
  dictionaryUuid: string;
}

interface ConnectState {
  dictionaryState: StoreStatePerId;
}
interface ConnectDispatch {
  fetchDictionary: typeof fetchDictionary;
  dictionaryUpdated: typeof dictionaryUpdated;
  saveDictionary: typeof saveDictionary;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ dictionaryEditor }, { dictionaryUuid }) => ({
      dictionaryState: dictionaryEditor[dictionaryUuid]
    }),
    {
      fetchDictionary,
      dictionaryUpdated,
      saveDictionary
    }
  )
);

const DictionaryEditor = ({
  dictionaryUuid,
  dictionaryState,
  dictionaryUpdated,
  fetchDictionary,
  saveDictionary
}: EnhancedProps) => {
  useEffect(() => {
    fetchDictionary(dictionaryUuid);
  });

  if (!dictionaryState) {
    return <Loader message={`Loading Dictionary ${dictionaryUuid}`} />;
  }

  const { dictionary, isDirty, isSaving } = dictionaryState;

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "save",
      disabled: !(isDirty || isSaving),
      title: isSaving ? "Saving..." : isDirty ? "Save" : "Saved",
      onClick: () => saveDictionary(dictionaryUuid)
    }
  ];

  return (
    <DocRefEditor docRefUuid={dictionaryUuid} actionBarItems={actionBarItems}>
      <textarea
        value={dictionary && dictionary.data}
        onChange={({ target: { value } }) =>
          dictionaryUpdated(dictionaryUuid, { data: value })
        }
      />
    </DocRefEditor>
  );
};

export default enhance(DictionaryEditor);

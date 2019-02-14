/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import * as React from "react";
import { useEffect, useState } from "react";
import { compose } from "recompose";
import { connect } from "react-redux";

import Loader from "../Loader";
import ExpressionOperator from "./ExpressionOperator";
import DeleteExpressionItem, {
  useDialog as useDeleteItemDialog
} from "./DeleteExpressionItem";
import { GlobalStoreState } from "../../startup/reducers";
import { DataSourceType, StyledComponentProps } from "../../types";
import { StoreStateById, actionCreators } from "./redux";
import ROExpressionBuilder from "./ROExpressionBuilder";

const { expressionItemDeleted } = actionCreators;

export interface Props extends StyledComponentProps {
  dataSource: DataSourceType;
  expressionId: string;
  showModeToggle?: boolean;
  editMode?: boolean;
}

interface ConnectState {
  expressionState: StoreStateById;
}
interface ConnectDispatch {
  expressionItemDeleted: typeof expressionItemDeleted;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId]
    }),
    { expressionItemDeleted }
  )
);

const ExpressionBuilder = ({
  expressionId,
  dataSource,
  expressionState,
  showModeToggle: smtRaw,
  editMode,
  expressionItemDeleted
}: EnhancedProps) => {
  const [inEditMode, setEditableByUser] = useState<boolean>(false);

  useEffect(() => {
    setEditableByUser(editMode || false);
  }, []);

  const {
    showDialog: showDeleteItemDialog,
    componentProps: deleteDialogComponentProps
  } = useDeleteItemDialog(itemId =>
    expressionItemDeleted(expressionId, itemId)
  );

  if (!expressionState) {
    return <Loader message="Loading expression state..." />;
  }
  const showModeToggle = smtRaw && !!dataSource;

  const { expression } = expressionState;

  return (
    <div>
      <DeleteExpressionItem {...deleteDialogComponentProps} />
      {showModeToggle ? (
        <React.Fragment>
          <label>Edit Mode</label>
          <input
            type="checkbox"
            checked={inEditMode}
            onChange={() => setEditableByUser(!inEditMode)}
          />
        </React.Fragment>
      ) : (
        undefined
      )}
      {inEditMode ? (
        <ExpressionOperator
          showDeleteItemDialog={showDeleteItemDialog}
          dataSource={dataSource}
          expressionId={expressionId}
          isRoot
          isEnabled
          operator={expression}
        />
      ) : (
        <ROExpressionBuilder
          expressionId={expressionId}
          expression={expression}
        />
      )}
    </div>
  );
};

export default enhance(ExpressionBuilder);

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
import {
  compose,
  withStateHandlers,
  lifecycle,
  StateHandlerMap,
  withHandlers
} from "recompose";
import { connect } from "react-redux";

import {
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
  StoreStateById as ExpressionBuilderStoreState
} from "../ExpressionBuilder";
import { processSearchString } from "./expressionSearchBarUtils";
import Button from "../Button";
import { DataSourceType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const { expressionChanged } = expressionBuilderActionCreators;

export interface Props {
  expressionId: string;
  dataSource: DataSourceType;
  onSearch: (expressionId: string) => void;
}

interface ConnectState {
  expressionState: ExpressionBuilderStoreState;
}

interface ConnectDispatch {
  expressionChanged: typeof expressionChanged;
}

interface WithState {
  isExpression: boolean;
  isExpressionVisible: boolean;
  searchString: string;
  isSearchStringValid: boolean;
  searchIsInvalid: boolean;
  searchStringValidationMessages: Array<string>;
}

interface WithHandlers {
  onChange: React.ChangeEventHandler<HTMLInputElement>;
}

interface WithStateHandlers {
  setIsExpression: (isExpression: boolean) => WithState;
  setIsExpressionVisible: (isExpressionVisible: boolean) => WithState;
  setSearchString: (searchString: string) => WithState;
  setIsSearchStringValid: (isSearchStringValid: boolean) => WithState;
  setSearchStringValidationMessages: (
    searchStringValidationMessages: Array<string>
  ) => WithState;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithState,
    WithStateHandlers,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId]
    }),
    { expressionChanged }
  ),
  withStateHandlers<WithState, WithStateHandlers & StateHandlerMap<WithState>>(
    () => ({
      isExpression: false,
      isExpressionVisible: false,
      searchString: "",
      searchIsInvalid: false,
      isSearchStringValid: true,
      searchStringValidationMessages: []
    }),
    {
      setIsExpression: ({}) => (isExpression: boolean) => ({ isExpression }),
      setIsExpressionVisible: ({}) => (isExpressionVisible: boolean) => ({
        isExpressionVisible
      }),
      setSearchString: ({}) => (searchString: string) => ({ searchString }),
      setIsSearchStringValid: ({}) => isSearchStringValid => ({
        isSearchStringValid
      }),
      setSearchStringValidationMessages: ({}) => searchStringValidationMessages => ({
        searchStringValidationMessages,
        searchIsInvalid: searchStringValidationMessages.length > 0
      })
    }
  ),
  lifecycle<
    Props & ConnectState & ConnectDispatch & WithState & WithStateHandlers,
    {}
  >({
    componentDidMount() {
      // We need to set up an expression so we've got something to search with,
      // even though it'll be empty.
      const { expressionChanged, expressionId, dataSource } = this.props;
      const parsedExpression = processSearchString(dataSource, "");
      expressionChanged(expressionId, parsedExpression.expression);

      const { onSearch } = this.props;
      onSearch(expressionId);
    }
  }),
  withHandlers<
    Props & ConnectState & ConnectDispatch & WithState & WithStateHandlers,
    WithHandlers
  >({
    onChange: ({
      expressionId,
      dataSource,
      searchString,
      setSearchString,
      setIsSearchStringValid,
      setSearchStringValidationMessages
    }) => ({ target: { value } }) => {
      const expression = processSearchString(dataSource, value);
      const invalidFields = expression.fields.filter(
        field =>
          !field.conditionIsValid || !field.fieldIsValid || !field.valueIsValid
      );

      const searchStringValidationMessages: Array<string> = [];
      if (invalidFields.length > 0) {
        invalidFields.forEach(invalidField => {
          searchStringValidationMessages.push(
            `'${invalidField.original}' is not a valid search term`
          );
        });
      }

      setIsSearchStringValid(invalidFields.length === 0);
      setSearchStringValidationMessages(searchStringValidationMessages);
      setSearchString(value);

      const parsedExpression = processSearchString(dataSource, searchString);
      expressionChanged(expressionId, parsedExpression.expression);
    }
  })
);

const ExpressionSearchBar = ({
  dataSource,
  expressionId,
  expressionChanged,
  searchString,
  isExpression,
  setIsExpression,
  expressionState,
  searchStringValidationMessages,
  onSearch,
  searchIsInvalid,
  onChange
}: EnhancedProps) => (
  <div className="dropdown search-bar borderless">
    <div className="search-bar__header">
      <input
        placeholder="I.e. field1=value1 field2=value2"
        value={isExpression ? expressionState.expressionAsString : searchString}
        className="search-bar__input"
        onChange={onChange}
      />
      <Button
        disabled={searchIsInvalid}
        icon="search"
        onClick={() => {
          onSearch(expressionId);
        }}
      />
    </div>
    <div tabIndex={0} className={`dropdown__content search-bar__content`}>
      <div className="search-bar__content__header">
        <Button
          text="Text search"
          selected={!isExpression}
          icon="i-cursor"
          className="search-bar__modeButton raised-low bordered hoverable"
          onClick={() => {
            setIsExpression(false);
          }}
        />
        <Button
          text="Expression search"
          selected={isExpression}
          disabled={searchIsInvalid}
          className="search-bar__modeButton raised-low bordered hoverable"
          icon="edit"
          onClick={() => {
            if (!isExpression) {
              const parsedExpression = processSearchString(
                dataSource,
                searchString
              );
              expressionChanged(expressionId, parsedExpression.expression);
              setIsExpression(true);
            }
          }}
        />
      </div>
      {isExpression ? (
        <ExpressionBuilder
          className="search-bar__expressionBuilder"
          showModeToggle={false}
          editMode
          dataSource={dataSource}
          expressionId={expressionId}
        />
      ) : (
        <div>{searchStringValidationMessages}</div>
      )}
    </div>
  </div>
);

export default enhance(ExpressionSearchBar);

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

import { processSearchString } from "./expressionSearchBarUtils";
import Button from "../Button";
import { toString } from "../ExpressionBuilder/expressionUtils";
import { ExpressionBuilder } from "../ExpressionBuilder";
import {
  DataSourceType,
  ExpressionOperatorType,
} from "../ExpressionBuilder/types";

interface Props {
  className?: string;
  dataSource: DataSourceType;
  onSearch: (e: ExpressionOperatorType) => void;
  initialSearchString?: string;
  initialSearchExpression?: ExpressionOperatorType;
}

const defaultSearchExpression: ExpressionOperatorType = {
  type: "operator",
  op: "AND",
  enabled: true,
  children: [],
};

const ExpressionSearchBar: React.FunctionComponent<Props> = ({
  initialSearchString,
  initialSearchExpression,
  dataSource,
  onSearch,
}) => {
  const [isExpression, setIsExpression] = React.useState<boolean>(false);
  const [expression, setExpression] = React.useState<ExpressionOperatorType>(
    initialSearchExpression || defaultSearchExpression,
  );
  const [searchString, setSearchString] = React.useState<string>(
    initialSearchString || "",
  );
  const [isSearchStringValid, setIsSearchStringValid] = React.useState<boolean>(
    true,
  );
  const [
    searchStringValidationMessages,
    setSearchStringValidationMessages,
  ] = React.useState<string[]>([]);

  React.useEffect(() => {
    if (!expression) {
      const { expression } = processSearchString(dataSource, "");
      setExpression(expression);
    }

    onSearch(expression);
  }, [dataSource, expression, onSearch, setExpression]);

  const onChange: React.ChangeEventHandler<HTMLInputElement> = ({
    target: { value },
  }) => {
    const { fields, expression } = processSearchString(dataSource, value);
    const invalidFields = fields.filter(
      (field) =>
        !field.conditionIsValid || !field.fieldIsValid || !field.valueIsValid,
    );

    const searchStringValidationMessages: string[] = [];
    if (invalidFields.length > 0) {
      invalidFields.forEach((invalidField) => {
        searchStringValidationMessages.push(
          `'${invalidField.original}' is not a valid search term`,
        );
      });
    }

    setIsSearchStringValid(invalidFields.length === 0);
    setSearchStringValidationMessages(searchStringValidationMessages);
    setSearchString(value);
    setExpression(expression);
  };

  const onClickSearch = React.useCallback(() => {
    onSearch(expression);
  }, [onSearch, expression]);
  const onClickSetTextSearch = React.useCallback(() => {
    setIsExpression(false);
  }, [setIsExpression]);
  const onClickSetExpressionSearch = React.useCallback(() => {
    if (!isExpression) {
      const { expression } = processSearchString(dataSource, searchString);
      setExpression(expression);
      setIsExpression(true);
    }
  }, [dataSource, searchString, isExpression, setExpression, setIsExpression]);

  return (
    <div className="dropdown search-bar control borderless">
      <div className="search-bar__header">
        <input
          placeholder="I.e. field1=value1 field2=value2"
          value={
            isExpression && !!expression ? toString(expression) : searchString
          }
          className="search-bar__input control"
          onChange={onChange}
        />
        <Button
          className="search-bar__button"
          disabled={!isSearchStringValid}
          appearance="icon"
          icon="search"
          onClick={onClickSearch}
        />
      </div>
      <div
        tabIndex={0}
        className={`dropdown__content search-bar__content control`}
      >
        <div className="search-bar__content__header">
          <Button
            selected={!isExpression}
            icon="i-cursor"
            className="search-bar__modeButton raised-low bordered hoverable"
            onClick={onClickSetTextSearch}
          >
            Text search
          </Button>
          <Button
            selected={isExpression}
            disabled={!isSearchStringValid}
            className="search-bar__modeButton raised-low bordered hoverable"
            icon="edit"
            onClick={onClickSetExpressionSearch}
          >
            Expression search
          </Button>
        </div>
        {isExpression && !!expression ? (
          <ExpressionBuilder
            className="search-bar__expressionBuilder"
            showModeToggle={false}
            editMode
            dataSource={dataSource}
            value={expression}
            onChange={setExpression}
          />
        ) : (
          <div>{searchStringValidationMessages}</div>
        )}
      </div>
    </div>
  );
};

export default ExpressionSearchBar;

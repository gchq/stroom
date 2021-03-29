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
import { ConnectDragSource, ConnectDropTarget } from "react-dnd";
import { DictionaryDoc } from "components/DocumentEditors/useDocumentApi/types/dictionaryDoc";

export enum DragDropTypes {
  OPERATOR = "operator",
  TERM = "term",
}

export interface DragCollectedProps {
  connectDragSource: ConnectDragSource;
  isDragging: boolean;
}
export interface DropCollectedProps {
  connectDropTarget: ConnectDropTarget;
  isOver: boolean;
  canDrop: boolean;
}

export interface DragObject {
  expressionItem: ExpressionOperatorType | ExpressionTermType;
}

interface ConditionTypes<T> {
  EQUALS: T;
  IN: T;
  IN_DICTIONARY: T;
  IS_DOC_REF: T;
  CONTAINS: T;
  BETWEEN: T;
  GREATER_THAN: T;
  GREATER_THAN_OR_EQUAL_TO: T;
  LESS_THAN: T;
  LESS_THAN_OR_EQUAL_TO: T;
}

export const ConditionDisplayValues: ConditionTypes<string> = {
  EQUALS: "=",
  IN: "in",
  IN_DICTIONARY: "in dictionary",
  IS_DOC_REF: "is DocRef",
  CONTAINS: "contains",
  BETWEEN: "between",
  GREATER_THAN: ">",
  GREATER_THAN_OR_EQUAL_TO: ">=",
  LESS_THAN: "<",
  LESS_THAN_OR_EQUAL_TO: "<=",
};

export type ConditionType = keyof ConditionTypes<any>;
export const conditionTypes: ConditionType[] = Object.keys(
  ConditionDisplayValues,
).map((d) => d as ConditionType);

export interface DataSourceFieldType {
  type: "ID" | "FIELD" | "NUMERIC_FIELD" | "DATE_FIELD";
  name: string;
  queryable: boolean;
  conditions: ConditionType[];
}

export interface DataSourceType {
  fields: DataSourceFieldType[];
}

export interface ExpressionItem {
  type: "operator" | "term";
  enabled: boolean;
}

export type OperatorType = "AND" | "OR" | "NOT";
export const OperatorTypeValues: OperatorType[] = ["AND", "OR", "NOT"];

export interface ExpressionOperatorType extends ExpressionItem {
  type: "operator";
  op: OperatorType;
  children: (ExpressionTermType | ExpressionOperatorType)[];
}

export interface ExpressionTermType extends ExpressionItem {
  type: "term";
  field?: string;
  condition?: ConditionType;
  value?: any;
  dictionary?: DictionaryDoc | null;
}

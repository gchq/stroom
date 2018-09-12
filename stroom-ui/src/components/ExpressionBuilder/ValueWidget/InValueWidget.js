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

import React from 'react';
import { compose, withHandlers, withProps, withStateHandlers } from 'recompose';

import Button from 'components/Button';

const enhance = compose(
  withProps(({ value }) => {
    const hasValues = !!value && value.length > 0;
    const splitValues = hasValues ? value.split(',') : [];

    return {
      splitValues,
    };
  }),
  withStateHandlers(
    ({ composingValue = '', inputHasFocus = false }) => ({
      composingValue,
      inputHasFocus,
    }),
    {
      onInputFocus: () => () => ({ inputHasFocus: true }),
      onInputBlur: () => () => ({ inputHasFocus: false }),
      onInputChange: () => ({ target: { value } }) => ({ composingValue: value }),
      onInputSubmit: ({ composingValue }, { splitValues, onChange }) => () => {
        const newValue = splitValues
          .filter(s => s !== composingValue)
          .concat([composingValue])
          .join();
        onChange(newValue);

        return { composingValue: '' };
      },
    },
  ),
  withHandlers({
    onInputKeyDown: ({ onInputSubmit }) => (e) => {
      if (e.key === 'Enter') {
        onInputSubmit();
      }
    },
    onTermDelete: ({ splitValues, onChange }) => (term) => {
      const newValue = splitValues.filter(s => s !== term).join();
      onChange(newValue);
    },
  }),
  withProps(({ value, composingValue, inputHasFocus }) => ({
    valueToShow: inputHasFocus ? composingValue : value,
  })),
);

const InValueWidget = ({
  onInputFocus,
  onInputBlur,
  onInputChange,
  onInputKeyDown,
  splitValues,
  valueToShow,
  onTermDelete,
}) => (
    <div className="dropdown">
      <input
        placeholder="Type and hit 'Enter'"
        value={valueToShow}
        onFocus={onInputFocus}
        onBlur={onInputBlur}
        onChange={onInputChange}
        onKeyDown={onInputKeyDown}
      />
      <div className="dropdown__content">
        {splitValues.map(k => (
          <div key={k}>
            {k}
            <Button onClick={e => onTermDelete(k)} text='X' />
          </div>
        ))}
      </div>
    </div>
  );

export default enhance(InValueWidget);

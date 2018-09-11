import React from 'react';

import { compose, withHandlers, withProps, withStateHandlers } from 'recompose';

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
          <button onClick={e => onTermDelete(k)}>X</button>
        </div>
      ))}
    </div>
  </div>
);

export default enhance(InValueWidget);

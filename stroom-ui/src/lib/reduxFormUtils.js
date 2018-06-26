import React from 'react';

export const required = value => (value ? undefined : 'Required');
export const minLength = min => value =>
  (value && value.length < min ? `Must be ${min} characters or more` : undefined);
export const minLength2 = minLength(2);

export const renderField = ({
  input,
  label,
  type,
  meta: { touched, error, warning },
  ...otherProps
}) => (
  <div>
    <label>{label}</label>
    <div>
      <input {...input} placeholder={label} type={type} {...otherProps} />
      {touched && ((error && <span>{error}</span>) || (warning && <span>{warning}</span>))}
    </div>
  </div>
);

export const truncate = (text, limit) =>
  (text.length > limit ? `${text.substr(0, limit)}...` : text);

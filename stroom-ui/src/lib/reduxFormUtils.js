export const required = value => (value ? undefined : 'Required');

export const minLength = min => value =>
  (value && value.length < min ? `Must be ${min} characters or more` : undefined);
export const minLength2 = minLength(2);

export const truncate = (text, limit) =>
  (text.length > limit ? `${text.substr(0, limit)}...` : text);

export const updateIdSubstate = (state, id, defaults, updates) => ({
  ...state,
  [id]: {
    ...defaults,
    ...state[id],
    ...updates,
  },
});

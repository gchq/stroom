export default () => (dispatch, getState) => {
  const {
    form: {
      newDocRef: { values },
    },
  } = getState();

  console.log('Creating New Doc Ref', values);
};

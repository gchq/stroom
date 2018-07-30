import { compose, branch, renderNothing } from 'recompose';
import { connect } from 'react-redux';

export default actions =>
  compose(
    connect(
      ({ xslt }, { xsltId }) => ({
        xslt: xslt[xsltId],
      }),
      actions,
    ),
    branch(({ xslt }) => !xslt, renderNothing),
  );

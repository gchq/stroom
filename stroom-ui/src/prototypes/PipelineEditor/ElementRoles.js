export const ElementRoles = {
  SOURCE: 'source',
  DESTINATION: 'destination',
  TARGET: 'target',
  HAS_TARGETS: 'hasTargets',
  READER: 'reader',
  PARSER: 'parser',
  WRITER: 'writer',

  /**
   * Pipeline elements that mutate the input provided to them to produce
   * different output, e.g. XSLT filter.
   */
  MUTATOR: 'mutator',
  /**
   * Pipeline elements that validate provided input and produce a set of
   * indicators to show where the input is invalid.
   */
  VALIDATOR: 'validator',
  /**
   * Pipeline elements that have code associated with them that alters their
   * behaviour, e.g. XSLT filter or various parser types.
   */
  HAS_CODE: 'hasCode',
  /**
   * Add this type to elements that we want to appear in the pipeline tree in
   * simple mode.
   */
  VISABILITY_SIMPLE: 'simple',
  /**
   * Add this type to elements that we want to appear in the pipeline tree in
   * stepping mode.
   */
  VISABILITY_STEPPING: 'stepping',
};

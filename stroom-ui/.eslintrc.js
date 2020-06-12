module.exports = {
  env: {
    browser: true,
    es6: true,
  },
  parser: "@typescript-eslint/parser",
  parserOptions: {
    project: "./tsconfig.json",
  },
  extends: [
    "plugin:@typescript-eslint/recommended",
    "plugin:react/recommended",
    "prettier",
    "prettier/react",
    "prettier/@typescript-eslint",
  ],
  settings: {
    react: {
      version: "detect",
    },
  },
  globals: {
    Atomics: "readonly",
    SharedArrayBuffer: "readonly",
  },
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
    },
    ecmaVersion: 2018,
    sourceType: "module",
  },
  plugins: ["react", "@typescript-eslint", "prettier"],
  rules: {
    "prettier/prettier": "warn",
    //"react/jsx-uses-vars": 1,
    // Indent with 2 spaces
    //indent: ["error", 2],
    // Indent JSX with 2 spaces
    //"react/jsx-indent": ["error", 2],
    // Indent props with 2 spaces
    //"react/jsx-indent-props": ["error", 2],
    //"react/jsx-wrap-multilines": [
    //"error",
    //{
    //declaration: false,
    //assignment: false,
    //},
    //],
    "linebreak-style": ["error", "unix"],
    //quotes: ["error", "single"],
    semi: ["error", "always"],

    // Don't think we need PropTypes as typescript handles typechecking
    "react/prop-types": 0,

    // This rule seems to force the use of ReactElement in this example
    //
    // export const DisplayPillChoice: React.FunctionComponent<Props> = ({
    //   pill,
    // }: Props): React.ReactElement => (
    "@typescript-eslint/explicit-function-return-type": "off",
    "@typescript-eslint/no-object-literal-type-assertion": "off",
    "@typescript-eslint/no-non-null-assertion": "off",
    "@typescript-eslint/explicit-member-accessibility": "off",
    "@typescript-eslint/no-explicit-any": "off",
  },
};

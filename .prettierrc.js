module.exports = {
  arrowParens: 'avoid',
  bracketSpacing: true,
  endOfLine: 'lf',
  plugins: ['prettier-plugin-java'],
  printWidth: 80,
  quoteProps: 'consistent',
  semi: true,
  singleQuote: true,
  tabWidth: 2,
  trailingComma: 'all',
  useTabs: false,
  overrides: [
    {
      files: '*.java',
      options: {
        printWidth: 140,
        tabWidth: 4,
      },
    },
  ],
};

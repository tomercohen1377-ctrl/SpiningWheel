/**
 * Babel configuration for the demo React Native app.
 *
 * Points Metro at the official RN 0.76 babel preset, which handles JSX,
 * Flow-stripping, Hermes bytecode generation, polyfills, etc.
 */
module.exports = {
  presets: ['module:@react-native/babel-preset'],
};

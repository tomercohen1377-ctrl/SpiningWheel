/**
 * Metro configuration for the demo React Native app.
 *
 * This wraps Metro's defaults (from @react-native/metro-config) so we get
 * the standard RN 0.76 behaviour with no custom overrides. If you need
 * extra asset extensions, resolver aliases, or transformer tweaks, this is
 * the place.
 */
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

/**
 * @type {import('metro-config').MetroConfig}
 */
const config = {};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);

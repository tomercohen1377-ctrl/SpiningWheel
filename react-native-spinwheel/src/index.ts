/**
 * react-native-spinwheel
 *
 * Public entry point.
 *
 * Usage:
 * ```ts
 * import SpinWheelModule from 'react-native-spinwheel';
 *
 * // Sync assets and update the home-screen widget
 * await SpinWheelModule.syncWidgetConfiguration('https://…/config.json');
 *
 * // Get timestamp of last sync
 * const ts: number = await SpinWheelModule.getLastFetchTime();
 * ```
 */

import { NativeModules } from 'react-native';
import type { ISpinWheelModule } from './types';

const { SpinWheelModule } = NativeModules;

if (!SpinWheelModule) {
  throw new Error(
    '[react-native-spinwheel] SpinWheelModule native module not found.\n' +
    'Make sure you have added SpinWheelPackage to your MainApplication ' +
    'and rebuilt the app.'
  );
}

export default SpinWheelModule as ISpinWheelModule;
export type { ISpinWheelModule };

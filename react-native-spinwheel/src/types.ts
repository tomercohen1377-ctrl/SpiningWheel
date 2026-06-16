/**
 * react-native-spinwheel — TypeScript type definitions
 */

export interface ISpinWheelModule {
  /**
   * Downloads the JSON config (for spin settings) and all four image assets,
   * then refreshes every pinned Spin Wheel widget instance.
   *
   * The JSON's `network.assets.host` field is a placeholder — pass the real
   * asset download URLs explicitly.
   *
   * @param configUrl  Full HTTPS URL of the remote JSON configuration.
   * @param bgUrl      Direct download URL for the background image.
   * @param wheelUrl   Direct download URL for the spinning wheel image.
   * @param frameUrl   Direct download URL for the static frame overlay.
   * @param spinUrl    Direct download URL for the spin button image.
   */
  syncWidgetConfiguration(
    configUrl: string,
    bgUrl:     string,
    wheelUrl:  string,
    frameUrl:  string,
    spinUrl:   string
  ): Promise<boolean>;

  /** Epoch-ms of last successful sync, or 0 if never synced. */
  getLastFetchTime(): Promise<number>;

  /** Clears all cached images and the last-fetch timestamp. */
  clearCache(): Promise<boolean>;
}

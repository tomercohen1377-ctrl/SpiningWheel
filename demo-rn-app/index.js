/**
 * Demo Spin Wheel React Native App
 * Entry point — registers the root component with the RN runtime.
 */
import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);

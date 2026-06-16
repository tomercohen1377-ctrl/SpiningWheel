# Demo Spin Wheel App

A minimal React Native app demonstrating `react-native-spinwheel`.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Node.js | ≥ 18 |
| React Native CLI | Latest (`npm install -g react-native-cli`) |
| Android Studio | Latest with SDK 35 |
| Java | 17 |

---

## Setup

### 1 – Pack the library

```bash
cd ../react-native-spinwheel
npm install
npm pack
# Produces: react-native-spinwheel-1.0.0.tgz
```

### 2 – Install dependencies

```bash
cd ../demo-rn-app
npm install
```

The `package.json` references the library via `"file:../react-native-spinwheel"`,
so the pack step above is optional for local development.

### 3 – Configure your wheel JSON URL

Edit `App.tsx` line:
```ts
const DEFAULT_CONFIG_URL = 'https://example.com/wheel-config.json';
```
Replace with your actual hosted config URL (see `docs/API.md` for schema).

### 4 – Run on Android

```bash
npx react-native run-android
```

> Make sure an Android emulator is running or a device is connected.

---

## What You'll See

1. A header bar with the app title.
2. A text input where you can paste any config URL.
3. The live spin wheel rendered natively via the `SpinWheelView` component.
4. A result badge and an alert after each spin.

---

## Installing from .tgz in any RN project

```bash
npm install ./path/to/react-native-spinwheel-1.0.0.tgz
```

Then register the package in your `MainApplication.kt`:
```kotlin
import com.spinwheel.SpinWheelPackage

override fun getPackages(): List<ReactPackage> =
    PackageList(this).packages.apply {
        add(SpinWheelPackage())
    }
```

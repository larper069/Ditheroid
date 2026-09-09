# Ditheroid

<p align="center">
  <b>Real-time GPU-powered dithering camera for Android.</b><br>
  Turn the world into ordered pixel art, live.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android&logoColor=white" alt="Android 14+">
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin + Compose">
  <img src="https://img.shields.io/badge/OpenGL%20ES-2.0-5586A4" alt="OpenGL ES 2.0">
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="GPL-3.0">
</p>

---

## What is Ditheroid?

**Ditheroid** is a free and open-source Android camera app that applies dithering effects **in real time** using the GPU.

Instead of taking a normal photo and processing it later, Ditheroid lets you see the final pixel-art look directly through the camera preview before you capture it.

## Screenshots

<p align="center">
  <img src="screenshots/ditheroid-1.png" width="30%" alt="Ditheroid camera preview">
  &nbsp;
  <img src="screenshots/ditheroid-2.png" width="30%" alt="Ditheroid palette controls">
  &nbsp;
  <img src="screenshots/ditheroid-3.png" width="30%" alt="Ditheroid dither settings">
</p>

## Features

- Real-time GPU dithering
- Bayer **2×2, 4×4 and 8×8** matrices
- Multiple pixel shapes: square, circle, plus, cross, diamond and dot
- Dense **ULTRA / FINE / HIGH** virtual-resolution modes
- Adjustable dither strength
- Custom **2–8 color palettes**
- Built-in palette presets
- Save and reuse custom templates
- Processed photo capture — the saved image contains the actual dither effect
- CameraX + OpenGL ES rendering pipeline
- No account, ads or subscription
- Free and open source

## Download

The easiest way to install Ditheroid is from the **Releases** section of this repository.

1. Open **Releases**
2. Download the latest `Ditheroid-v*.apk`
3. Open the APK on your Android device
4. Allow installation from your browser/file manager if Android asks
5. Install and launch Ditheroid

> Ditheroid currently requires **Android 14 or newer**.

## Build from source

Clone the repository:

```bash
git clone https://github.com/larper069/Ditheroid.git
cd Ditheroid
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

The APK will be generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it through ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tech

Ditheroid is built with:

- Kotlin
- Jetpack Compose
- CameraX
- OpenGL ES 2.0
- GLSL shaders
- Android Gradle Plugin

The live camera frame is passed through an OpenGL shader where palette quantization and ordered dithering are applied before the image is displayed or captured.

## Why open source?

Ditheroid was made as a fun experiment in real-time graphics, pixel art and mobile camera processing.

It is released for anyone to **use, study, modify and improve**.

If you build something cool from it, have fun with it.

## License

Ditheroid is licensed under the **GNU General Public License v3.0**.

See [`LICENSE`](LICENSE) for the full license text.

---

<p align="center">
  <b>Dither everything.</b>
</p>

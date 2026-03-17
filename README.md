# SnapJe! ??

A modern Android photo gallery application built with Jetpack Compose and Material Design 3. Browse, organize, and manage your photos with a clean, intuitive interface.

![SnapJe! Banner](docs/banner.png)

## ? Features

### Photo Management
- **Photo Browsing** - View photos organized by folders/categories
- **Multi-Select** - Drag-to-select gesture for batch operations
- **Search** - Quick search through all your photos
- **Photo Operations** - Copy, move, rename, and share photos

### Gallery Experience
- **Fullscreen Viewer** - Immersive full-screen photo display
- **Pinch-to-Zoom** - Smooth zoom gestures (up to 5x magnification)
- **Double-Tap Zoom** - Quick zoom toggle with double-tap
- **Pan & Navigate** - Pan when zoomed, swipe between photos when not

### Smart Organization
- **Category Grid** - Beautiful animated category cards
- **Smooth Transitions** - Optimized navigation with hardware acceleration
- **Empty States** - Elegant illustrations for empty folders

### Trash & Recovery
- **Recently Deleted** - Trash folder with 30-day retention
- **Restore Photos** - Easily recover deleted items
- **Batch Delete** - Select and delete multiple photos at once

## ?? Screenshots

| Home Screen | Category View | Fullscreen | Trash |
|-------------|---------------|------------|-------|
| ![Home](docs/screenshots/home.png) | ![Category](docs/screenshots/category.png) | ![Fullscreen](docs/screenshots/fullscreen.png) | ![Trash](docs/screenshots/trash.png) |

## ??? Tech Stack

| Component | Technology |
|-----------|------------|
| **Language** | Kotlin 2.2.10 |
| **UI Framework** | Jetpack Compose (BOM 2025.05.00) |
| **Architecture** | MVVM + Clean Architecture |
| **Dependency Injection** | Hilt 2.54 |
| **Image Loading** | Coil 2.5.0 |
| **Navigation** | Navigation Compose 2.9.0 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |

## ?? Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35

### Build Commands

```powershell
# Debug build
.\gradlew.bat assembleDebug

# Release build
.\gradlew.bat assembleRelease

# Install on connected device
.\gradlew.bat installDebug

# Clean build
.\gradlew.bat clean

# Run all tests
.\gradlew.bat test

# Run lint checks
.\gradlew.bat lint
```

### Install APK

```powershell
# Debug APK location
app\build\outputs\apk\debug\app-debug.apk

# Release APK location
app\build\outputs\apk\release\app-release.apk
```

## ?? Project Structure

```
SnapJe!/
??? app/                          # Main application module
?   ??? src/main/
?   ?   ??? java/com/rapii/snapje/
?   ?   ?   ??? data/             # Data layer
?   ?   ?   ?   ??? FileOperations.kt
?   ?   ?   ?   ??? PhotoRepository.kt
?   ?   ?   ?   ??? TrashRepository.kt
?   ?   ?   ?   ??? models.kt
?   ?   ?   ??? di/               # Hilt DI modules
?   ?   ?   ??? domain/           # Business logic
?   ?   ?   ??? navigation/       # Navigation graph
?   ?   ?   ??? ui/               # UI layer (Composables)
?   ?   ?   ?   ??? PhotoXHomeScreen.kt
?   ?   ?   ?   ??? CategoryDetailScreen.kt
?   ?   ?   ?   ??? PhotoGalleryScreen.kt
?   ?   ?   ?   ??? RecentlyDeletedScreen.kt
?   ?   ?   ?   ??? components/   # Reusable components
?   ?   ?   ??? util/             # Utilities
?   ?   ?   ??? GalleryXApplication.kt
?   ?   ?   ??? MainActivity.kt
?   ?   ??? res/                  # Android resources
?   ?   ??? AndroidManifest.xml
?   ??? build.gradle.kts
??? core/                         # Core utilities module
??? data/                         # Shared data layer
??? domain/                       # Business logic layer
??? gradle/
    ??? libs.versions.toml        # Version catalog
```

## ??? Architecture

SnapJe! follows **MVVM + Clean Architecture** principles:

```
???????????????????
?   UI Layer      ?  ? Composables + ViewModels (Hilt)
???????????????????
?  Domain Layer   ?  ? Use Cases (Business Logic)
???????????????????
?   Data Layer    ?  ? Repositories + Data Sources
???????????????????
?  MediaStore     ?  ? Photo Storage & Trash Management
???????????????????
```

## ?? Design

- **Material Design 3** - Modern, adaptive UI components
- **Custom Icon** - Purple/blue gradient with photo frame design
- **Smooth Animations** - Hardware-accelerated transitions
- **Dark Mode Ready** - Theme system supports light/dark modes

## ?? Permissions

```xml
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- Android 12 and below -->
<uses-permission 
    android:name="android.permission.READ_EXTERNAL_STORAGE" 
    android:maxSdkVersion="32" />
```

## ?? Known Issues & Fixes

See [AGENTS.md](AGENTS.md) for detailed bug fixes and solutions:

- ? Scroll jumps after fullscreen - Fixed with offset tracking
- ? Copy fails in some categories - URI type detection added
- ? Photo quality degradation - Original size loading implemented
- ? Drag-to-select not working - Custom pointer handler added
- ? Batch delete issues - Single trash request for all URIs
- ? Restored photo thumbnail missing - PhotoRestoreEventManager added

## ?? License

This project is open source. Feel free to use, modify, and distribute.

## ?? Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## ?? Contact

For issues or questions, please open an issue on the GitHub repository.

---

**SnapJe! v1.1** - Built with ?? using Jetpack Compose

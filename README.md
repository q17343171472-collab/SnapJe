# SnapJe! 📸

A modern, feature-rich Android photo gallery application built with **Jetpack Compose** and **Kotlin**. SnapJe! provides a clean, intuitive interface for browsing, organizing, and managing your photos with smooth animations and Material Design 3 aesthetics.

## ✨ Why SnapJe!?

- **Modern UI** - Built entirely with Jetpack Compose for smooth, native Android experience
- **🏗️ Clean Architecture** - MVVM + Clean Architecture pattern for maintainability
- **⚡ Performance** - Hardware-accelerated transitions and optimized image loading
- **🗑️ Smart Trash** - 30-day retention with easy restore functionality
- **🎨 Material Design 3** - Beautiful, adaptive UI that follows Android design guidelines
- **🔍 Powerful Search** - Quick search through all your photos
- **📁 Category Browsing** - Organized folder/category view with animated grids
- **🖐️ Multi-Select** - Intuitive drag-to-select gesture for batch operations

## 🛠️ Built With

| Technology | Purpose |
|------------|---------|
| Kotlin 2.2.10 | Primary language |
| Jetpack Compose | Modern UI toolkit |
| Hilt | Dependency injection |
| Coil | Image loading |
| Room | Local database |
| Navigation Compose | In-app navigation |
| Material Design 3 | UI components & theming |

## 📋 Features

### Photo Management
- Browse photos organized by folders/categories
- Full-screen photo viewer with pinch-to-zoom (up to 5x)
- Double-tap to zoom, swipe to navigate
- Copy, move, rename, and share photos
- Batch operations with multi-select

### Smart Organization
- Animated category grid with Material Design cards
- Search functionality across all photos
- Custom sorting options (date, name, size)
- Empty state illustrations for better UX

### Trash & Recovery
- Recently Deleted folder with 30-day retention
- Batch delete with confirmation
- Easy restore functionality
- Automatic cleanup of expired items

## 🚀 Quick Start

```bash
# Clone the repository
git clone https://github.com/rapierrevorn/SnapJe.git

# Build debug APK
.\gradlew.bat assembleDebug

# Install on device
.\gradlew.bat installDebug
```

## 📄 License

This project is open source and available under the [MIT License](LICENSE).

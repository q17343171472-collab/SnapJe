# SnapJe! Architecture Documentation

## Overview

SnapJe! is a modern Android photo gallery application built with Jetpack Compose, following Clean Architecture principles with MVVM pattern.

## Architecture Layers

### 1. Presentation Layer (`:app` module)
- **UI Components**: Jetpack Compose screens and composables
- **ViewModels**: Manage UI state and business logic coordination
- **State Management**: StateFlow for reactive UI updates

**Key Files:**
- `ui/PhotoGalleryScreen.kt` - Main gallery view
- `ui/CategoryDetailScreen.kt` - Album/category detail view
- `ui/SettingsScreen.kt` - App settings
- `viewmodel/*ViewModel.kt` - Screen ViewModels

### 2. Domain Layer (`:domain` module)
- **Use Cases**: Encapsulate specific business logic
- **Models**: Core business entities (Category, PhotoItem)

**Key Files:**
- `domain/usecase/GetCategoriesUseCase.kt` - Retrieve categories
- `domain/model/` - Business entities

### 3. Data Layer (`:app/data` package)
- **Repositories**: Data access abstraction
- **Local Storage**: Room database for caching
- **Remote/MediaStore**: Access to device photos
- **Settings**: DataStore for preferences

**Key Files:**
- `data/PhotoRepository.kt` - MediaStore access
- `data/CachedPhotoRepository.kt` - Caching layer with Room
- `data/TrashRepository.kt` - Recently deleted photos
- `data/SettingsManager.kt` - Preferences management
- `data/local/*Dao.kt` - Room DAOs

## Data Flow

```
User Action → Screen Composable → ViewModel → UseCase → Repository → Data Source
                                                              ↓
Response ← StateFlow Update ← ViewModel ← UseCase ← Repository ← Data Source
```

### Cache Strategy
1. Load from Room cache immediately (fast)
2. Display cached data to user
3. Refresh from MediaStore in background
4. Update cache with fresh data

## Dependency Injection

Hilt provides dependency injection across all layers:
- `@Singleton` for repositories and managers
- `@HiltViewModel` for ViewModels
- Module definitions in `di/` package

## Key Design Decisions

### Fake Multi-Module Structure
The project has module declarations (`:core`, `:data`, `:domain`) but all code resides in `:app`. This was a scaffolding choice that should be refactored.

### Caching Approach
Two-tier caching:
- **Room Database**: Structured metadata (categories, photo info)
- **Coil**: Image loading and disk caching

### Paging Implementation
PhotoRepository supports paging via Android Paging3 library for efficient large dataset handling.

## Current Issues & Technical Debt

### Structural Problems
1. **Fake Modules**: Empty `:core`, `:data`, `:domain` modules while code lives in `:app`
2. **God Classes**: 
   - `CategoryDetailScreen.kt` (811 lines)
   - `PhotoGalleryScreen.kt` (635 lines)
   - `FileOperations.kt` (454 lines)

### Performance Concerns
1. No pagination in category detail screens despite Pager availability
2. Crossfade animations causing scroll lag (partially fixed)
3. Loading all photos at once in some views

### Maintainability Risks
1. Tight coupling between ViewModels and concrete repository implementations
2. Duplicate MediaStore query logic across repository methods
3. Inconsistent error handling patterns
4. Limited test coverage (many placeholder tests)

### Code Duplication
- MediaStore projection arrays defined multiple times
- Similar cursor iteration logic in different repository methods
- Permission checking duplicated across screens

## Refactoring Roadmap

### Phase 1: Immediate Improvements (Completed)
- ✅ Remove backup files (.bak) from version control
- ✅ Implement clearCache() functionality
- ✅ Add real unit tests replacing placeholders
- ✅ Create first UseCase (GetCategoriesUseCase)

### Phase 2: Component Extraction (Next)
- Extract reusable composables from large screen files
- Split FileOperations into focused utility classes
- Create shared permission handling component

### Phase 3: Architecture Cleanup
- Move code to proper modules (:domain, :data, :core)
- Introduce more UseCases for business logic
- Implement repository interfaces consistently

### Phase 4: Performance Optimization
- Enable pagination in all photo list views
- Optimize image loading with better Coil configuration
- Add memory monitoring and leak detection

### Phase 5: Testing
- Increase unit test coverage to >70%
- Add integration tests for repositories
- Implement UI tests with Compose Testing

## Testing Strategy

### Unit Tests
- Repository logic with mocked dependencies
- UseCase business logic
- Utility functions

### Integration Tests
- Repository + Database interactions
- ViewModel + Repository flows

### UI Tests
- Screen rendering and interactions
- Navigation flows

## Building & Running

```bash
# Install dependencies
pnpm install

# Run tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Run on device
./gradlew installDebug
```

## Module Responsibilities

| Module | Responsibility | Status |
|--------|---------------|--------|
| `:app` | UI, ViewModels, DI setup | Active |
| `:domain` | Use cases, business models | Partially implemented |
| `:data` | Repositories, data sources | Empty (code in :app) |
| `:core` | Utilities, extensions | Empty |

## Future Considerations

1. **Real Multi-Module**: Properly separate code into modules
2. **Feature Modules**: Dynamic feature modules for advanced features
3. **Modularization**: Feature-based rather than layer-based modules
4. **KMP**: Potential Kotlin Multiplatform for core logic

---

*Last Updated: 2024*
*Version: 1.1.0*

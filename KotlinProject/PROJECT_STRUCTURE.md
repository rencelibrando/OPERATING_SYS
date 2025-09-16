# WordBridge - Kotlin Compose Multiplatform Project

## 🏗️ Project Structure

This project follows **Clean Architecture** principles with a modular approach for maintainability and scalability.

```
src/jvmMain/kotlin/org/example/project/
├── domain/                     # Domain Layer (Business Logic)
│   └── model/                  # Domain Models
│       ├── User.kt             # User entity
│       ├── NavigationItem.kt   # Navigation data model
│       └── LearningActivity.kt # Learning activity model
│
├── presentation/               # Presentation Layer (UI Logic)
│   └── viewmodel/             # ViewModels (MVVM Pattern)
│       └── HomeViewModel.kt   # Home screen business logic
│
├── ui/                        # UI Layer (Compose Components)
│   ├── components/            # Reusable UI Components
│   │   ├── UserAvatar.kt      # User profile avatar
│   │   ├── ProgressCard.kt    # Progress display components
│   │   ├── LearningActivityCard.kt # Activity selection cards
│   │   ├── Sidebar.kt         # Navigation sidebar
│   │   └── ContinueLearningCard.kt # Main CTA component
│   │
│   ├── screens/               # Screen Composables
│   │   └── HomeScreen.kt      # Main dashboard screen
│   │
│   └── theme/                 # Design System
│       ├── Color.kt           # Color palette
│       ├── Typography.kt      # Text styles
│       └── Theme.kt           # Material3 theme configuration
│
├── App.kt                     # Root application component
├── main.kt                    # Application entry point
├── Platform.kt                # Platform-specific code
└── Greeting.kt                # Legacy greeting component
```

## 🎯 Architecture Overview

### **Clean Architecture Layers**

1. **Domain Layer** (`domain/`)
   - Contains business logic and entities
   - No dependencies on UI or external frameworks
   - Pure Kotlin classes with business rules

2. **Presentation Layer** (`presentation/`)
   - ViewModels following MVVM pattern
   - Manages UI state and business logic coordination
   - Uses Compose State for reactive UI updates

3. **UI Layer** (`ui/`)
   - Compose components and screens
   - Theme and design system
   - No business logic - only UI rendering

### **Key Design Patterns**

- **MVVM (Model-View-ViewModel)**: Separates UI from business logic
- **Repository Pattern**: Ready for data layer integration
- **Dependency Injection**: Structured for easy DI implementation
- **State Management**: Uses Compose State for reactive updates

## 🎨 Design System

### **Color Palette**
- **Primary**: Purple gradient theme (`#6366F1` to `#818CF8`)
- **Sidebar**: Dark gray theme (`#374151`, `#1F2937`)
- **Background**: Light gray (`#F9FAFB`) with white cards
- **Accent Colors**: Blue, Green, Orange, Red for different UI elements

### **Typography**
- Material3 typography system
- Custom font weights for hierarchy
- Consistent spacing and sizing

### **Components**
All components are designed to be:
- **Reusable**: Can be used across different screens
- **Configurable**: Accept parameters for customization
- **Accessible**: Follow Material3 accessibility guidelines
- **Responsive**: Adapt to different screen sizes

## 🚀 How to Add New Features

### **1. Adding a New Screen**

```kotlin
// 1. Create the screen composable
@Composable
fun NewScreen(
    viewModel: NewScreenViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    // Screen content
}

// 2. Create corresponding ViewModel
class NewScreenViewModel : ViewModel() {
    // State management
}

// 3. Add navigation item to NavigationItem.kt
NavigationItem(
    id = "new_feature",
    title = "New Feature",
    icon = "new_icon",
    route = "/new-feature"
)
```

### **2. Adding a New Component**

```kotlin
// Create in ui/components/
@Composable
fun NewComponent(
    // Parameters
    modifier: Modifier = Modifier
) {
    // Component implementation
}
```

### **3. Adding Domain Models**

```kotlin
// Create in domain/model/
data class NewEntity(
    val id: String,
    val name: String,
    // Other properties
) {
    companion object {
        fun sampleData() = NewEntity(/* sample data */)
    }
}
```

### **4. Extending the Theme**

```kotlin
// Add colors to Color.kt
object WordBridgeColors {
    val NewFeatureColor = Color(0xFF123456)
}

// Add typography styles to Typography.kt
val CustomTextStyle = TextStyle(/* custom style */)
```

## 🔧 Backend Integration Preparation

The project is structured to easily integrate with backend services:

### **Repository Pattern Setup**
```kotlin
// Create in data/repository/
interface UserRepository {
    suspend fun getUser(id: String): User
    suspend fun updateUser(user: User): User
}

class UserRepositoryImpl(
    private val apiService: ApiService
) : UserRepository {
    // Implementation
}
```

### **Dependency Injection Ready**
```kotlin
// The ViewModels are structured to accept repositories
class HomeViewModel(
    private val userRepository: UserRepository = UserRepositoryImpl()
) : ViewModel() {
    // Implementation
}
```

### **State Management**
- Uses Compose State for reactive UI
- Ready for integration with StateFlow/Flow
- Prepared for offline-first architecture

## 📱 Platform Support

The project is configured for:
- **Windows** (`.msi` packages)
- **macOS** (`.dmg` packages)  
- **Linux** (`.deb` packages)

## 🛠️ Development Guidelines

### **Naming Conventions**
- **Files**: PascalCase (e.g., `HomeScreen.kt`)
- **Classes**: PascalCase (e.g., `HomeViewModel`)
- **Functions**: camelCase (e.g., `onNavigationItemSelected`)
- **Variables**: camelCase (e.g., `navigationItems`)
- **Constants**: SCREAMING_SNAKE_CASE (e.g., `PRIMARY_PURPLE`)

### **Component Guidelines**
1. **Single Responsibility**: Each component has one clear purpose
2. **Composition over Inheritance**: Use composition for complex components
3. **Parameter Documentation**: Document all composable parameters
4. **Default Values**: Provide sensible defaults for optional parameters
5. **Modifier Parameter**: Always include a `Modifier` parameter

### **State Management Rules**
1. **Unidirectional Data Flow**: Data flows down, events flow up
2. **Single Source of Truth**: State is managed in ViewModels
3. **Immutable State**: Use data classes and immutable collections
4. **Event Handling**: Use callback functions for user interactions

## 🔄 Future Enhancements

The architecture supports easy addition of:
- **Navigation System**: Compose Navigation or custom routing
- **Networking Layer**: Retrofit, Ktor, or other HTTP clients
- **Database Integration**: Room, SQLDelight, or other databases
- **Dependency Injection**: Koin, Hilt, or manual DI
- **Testing Framework**: JUnit, Mockk, and Compose testing utilities
- **Internationalization**: String resources and locale support
- **Animations**: Compose animations and transitions
- **Accessibility**: Screen readers and keyboard navigation

## 📋 Getting Started

1. **Run the Application**:
   ```bash
   ./gradlew run
   ```

2. **Build for Distribution**:
   ```bash
   ./gradlew packageDistributionForCurrentOS
   ```

3. **Add New Features**:
   - Follow the patterns established in existing code
   - Maintain the clean architecture separation
   - Add appropriate tests for new functionality

This structure provides a solid foundation for building a comprehensive, maintainable, and scalable desktop application with Kotlin Compose Multiplatform.

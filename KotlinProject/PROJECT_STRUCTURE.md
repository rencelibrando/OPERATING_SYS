# WordBridge - Kotlin Compose Multiplatform Project

## 🏗️ Project Structure

This project follows **Clean Architecture** principles with a modular approach for maintainability and scalability.

```
src/jvmMain/kotlin/org/example/project/
├── data/                       # Data Layer (Repository Pattern)
│   ├── config/                # Configuration
│   │   └── SupabaseConfig.kt  # Supabase client setup & environment config
│   └── repository/            # Repository Interfaces
│       ├── UserRepository.kt  # User operations interface
│       ├── LearningRepository.kt # Learning progress & lessons interface
│       ├── VocabularyRepository.kt # Vocabulary management interface
│       └── AIChatRepository.kt # AI chat operations interface
│
├── domain/                     # Domain Layer (Business Logic)
│   └── model/                  # Domain Models
│       ├── User.kt             # User entity
│       ├── Profile.kt          # User profile & personal info
│       ├── Settings.kt         # User settings & preferences
│       ├── Progress.kt         # Learning progress & achievements
│       ├── Lesson.kt           # Lesson & course models
│       ├── Vocabulary.kt       # Vocabulary word models
│       ├── AIChat.kt           # AI chat & conversation models
│       ├── Speaking.kt         # Speaking practice models
│       ├── NavigationItem.kt   # Navigation data model
│       └── LearningActivity.kt # Learning activity model
│
├── presentation/               # Presentation Layer (UI Logic)
│   └── viewmodel/             # ViewModels (MVVM Pattern)
│       ├── HomeViewModel.kt   # Home screen business logic
│       ├── ProfileViewModel.kt # Profile management
│       ├── ProgressViewModel.kt # Progress tracking
│       ├── AIChatViewModel.kt  # AI chat functionality
│       └── VocabularyViewModel.kt # Vocabulary management
│
├── ui/                        # UI Layer (Compose Components)
│   ├── components/            # Reusable UI Components
│   │   ├── UserAvatar.kt      # User profile avatar
│   │   ├── ProgressCard.kt    # Progress display components
│   │   ├── LearningActivityCard.kt # Activity selection cards
│   │   ├── VocabularyFeatureCard.kt # Vocabulary feature cards
│   │   ├── VocabularyEmptyState.kt # Empty state components
│   │   ├── AIChatEmptyState.kt # AI chat empty state
│   │   ├── Sidebar.kt         # Navigation sidebar
│   │   └── ContinueLearningCard.kt # Main CTA component
│   │
│   ├── screens/               # Screen Composables
│   │   ├── HomeScreen.kt      # Main dashboard screen
│   │   ├── ProfileScreen.kt   # User profile management
│   │   ├── ProgressScreen.kt  # Learning progress tracking
│   │   ├── SettingsScreen.kt  # App settings & preferences
│   │   ├── AIChatScreen.kt    # AI tutoring chat interface
│   │   ├── VocabularyScreen.kt # Vocabulary learning
│   │   ├── LessonsScreen.kt   # Lesson browser
│   │   └── SpeakingScreen.kt  # Speaking practice
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

database/                       # Database Schema & Migrations
├── migrations/                 # Database migration files
│   └── 001_initial_schema.sql # Initial database schema
└── seeds/                     # Seed data
    └── 002_seed_data.sql      # Initial application data

Environment Configuration:
├── .env.example               # Environment variables template
├── .env.development          # Development environment config
└── .gitignore                # Updated to exclude sensitive files
```

## 🎯 Architecture Overview

### **Clean Architecture Layers**

1. **Data Layer** (`data/`)
   - **Repository Pattern**: Abstracts data sources (Supabase, cache, etc.)
   - **Configuration**: Supabase client setup and environment management
   - **Database Integration**: PostgreSQL via Supabase with real-time capabilities
   - **API Interfaces**: Defines contracts for data operations

2. **Domain Layer** (`domain/`)
   - Contains business logic and entities
   - No dependencies on UI or external frameworks
   - Pure Kotlin classes with business rules
   - Rich domain models for User, Learning, Vocabulary, AI Chat

3. **Presentation Layer** (`presentation/`)
   - ViewModels following MVVM pattern
   - Manages UI state and business logic coordination
   - Uses Compose State for reactive UI updates
   - Connects UI to data layer via repositories

4. **UI Layer** (`ui/`)
   - Compose components and screens
   - Theme and design system
   - No business logic - only UI rendering
   - Comprehensive screen coverage for all app features

### **Key Design Patterns**

- **MVVM (Model-View-ViewModel)**: Separates UI from business logic
- **Repository Pattern**: Data layer abstraction with Supabase integration
- **Clean Architecture**: Clear separation of concerns across layers
- **Dependency Injection**: Structured for easy DI implementation (ready for Koin/Hilt)
- **State Management**: Uses Compose State for reactive UI updates
- **Real-time Updates**: Supabase real-time subscriptions for live data
- **Row Level Security (RLS)**: Database-level security for user data protection

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
        fun sampleData() = NewEntity()
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

## 🗄️ Supabase Integration

### **Database Schema**
The project includes a complete PostgreSQL schema with:
- **User Management**: Profiles, settings, authentication
- **Learning Progress**: XP tracking, skill progression, achievements
- **Vocabulary System**: Words, user progress, spaced repetition
- **AI Chat**: Sessions, messages, bot personalities
- **Lessons**: Course content, user progress tracking

### **Repository Implementation**
```kotlin
// Data layer interfaces for clean architecture
interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile?>
    suspend fun signIn(email: String, password: String): Result<String>
    suspend fun signUp(email: String, password: String): Result<String>
}

interface LearningRepository {
    suspend fun getLearningProgress(userId: String): Result<LearningProgress?>
    suspend fun updateProgress(progress: LearningProgress): Result<Unit>
}

interface AIChatRepository {
    suspend fun sendMessage(sessionId: String, message: String): Result<ChatMessage>
    suspend fun generateAIResponse(context: Map<String, Any>): Result<String>
}
```

### **Real-time Features**
- **Live Chat**: Real-time AI conversation updates
- **Progress Sync**: Instant progress updates across devices  
- **Collaborative Learning**: Real-time study sessions (future feature)

### **Security & Privacy**
- **Row Level Security (RLS)**: Users can only access their own data
- **Authentication**: Supabase Auth with email/password and social login
- **Environment Security**: API keys and secrets in environment files
- **Data Encryption**: All sensitive data encrypted at rest

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

## 🚀 Current Technology Stack

### **Backend & Database**
- **Supabase**: PostgreSQL database with real-time capabilities
- **Authentication**: Supabase Auth with row-level security
- **File Storage**: Supabase Storage for user avatars and media
- **Edge Functions**: Serverless functions for OpenAI integration

### **Frontend & UI**
- **Kotlin Multiplatform**: Cross-platform development
- **Compose Multiplatform**: Modern declarative UI framework
- **Material 3**: Latest Material Design system
- **MVVM Pattern**: Clean separation of UI and business logic

### **AI Integration**
- **OpenAI API**: GPT models for AI tutoring and conversation
- **Vector Embeddings**: Semantic search for vocabulary
- **Real-time Chat**: Live AI conversation interface

### **Development Tools**
- **Gradle**: Build system with version catalogs
- **Environment Management**: Secure configuration handling
- **SQL Migrations**: Database schema versioning

## 🔄 Future Enhancements

The architecture supports easy addition of:
- **Navigation System**: Compose Navigation implementation
- **Dependency Injection**: Koin or Hilt integration
- **Testing Framework**: Comprehensive test coverage with JUnit & Mockk
- **Internationalization**: Multi-language support
- **Animations**: Advanced Compose animations and transitions
- **Accessibility**: Enhanced screen reader and keyboard navigation
- **Offline Support**: Local caching and sync capabilities
- **Push Notifications**: Learning reminders and progress updates
- **Social Features**: Study groups and leaderboards
- **Voice Recognition**: Speech-to-text for pronunciation practice

## 📋 Getting Started

### **1. Environment Setup**
```bash
# Copy environment template
cp .env.example .env.development


### **2. Database Setup**
1. Go to your Supabase project dashboard
2. Navigate to **SQL Editor**
3. Run `database/migrations/001_initial_schema.sql`
4. Run `database/seeds/002_seed_data.sql`

### **3. Run the Application**
```bash
# Development mode
./gradlew run

# With hot reload (if enabled)
./gradlew composeApp:run
```

### **4. Build for Distribution**
```bash
# Build for current OS
./gradlew packageDistributionForCurrentOS

# Build for specific platforms
./gradlew packageDistributionForWindows
./gradlew packageDistributionForMacOS  
./gradlew packageDistributionForLinux
```

### **5. Development Workflow**
- **Data Layer**: Add new repositories in `data/repository/`
- **Domain Models**: Extend models in `domain/model/`
- **UI Components**: Create reusable components in `ui/components/`
- **Screens**: Add new screens in `ui/screens/`
- **ViewModels**: Implement business logic in `presentation/viewmodel/`

### **6. Database Migrations**
When adding new features that require database changes:
1. Create new migration file: `database/migrations/00X_feature_name.sql`
2. Apply migration in Supabase SQL Editor
3. Update repository interfaces if needed
4. Test with existing data

## 🎯 Next Steps

This structure provides a solid foundation for building a comprehensive, maintainable, and scalable language learning application with:

- ✅ **Real-time AI Chat**: Powered by OpenAI and Supabase
- ✅ **Progress Tracking**: Comprehensive learning analytics
- ✅ **Vocabulary Management**: Spaced repetition system
- ✅ **User Management**: Secure authentication and profiles
- ✅ **Cross-platform**: Windows, macOS, and Linux support
- ✅ **Scalable Architecture**: Clean separation of concerns
- ✅ **Modern UI**: Material 3 design system

# WordBridge Admin - Lesson Topics Manager

A separate admin application for managing lesson topics in the WordBridge database.

## Features

- **Separate App Data**: Uses its own preferences storage, won't interfere with main app
- **View Topics**: Browse lesson topics by difficulty level (Beginner, Intermediate, Advanced)
- **Seed Topics**: Automatically seed beginner topics to Supabase
- **Topic Details**: View full details of each topic including ID, title, description, duration, and status

## Running the Admin App

### From IDE (IntelliJ IDEA / Android Studio)

1. Open the project
2. Navigate to `org.example.project.admin.AdminMainKt`
3. Right-click and select "Run 'AdminMainKt'"

### From Command Line

```bash
# Windows
.\gradlew.bat :composeApp:runAdmin

# macOS/Linux
./gradlew :composeApp:runAdmin
```

### Building Distributable

```bash
# Windows
.\gradlew.bat :composeApp:packageReleaseAdminMsi

# macOS
./gradlew :composeApp:packageReleaseAdminDmg

# Linux
./gradlew :composeApp:packageReleaseAdminDeb
```

## App Data Location

The admin app stores its preferences separately from the main app:

- **Windows**: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\example\project\admin\utils\wordbridge_admin`
- **macOS/Linux**: `~/.java/.userPrefs/org/example/project/admin/utils/wordbridge_admin`

This ensures the admin app won't interfere with the main WordBridge app's data.

## Usage

1. **Select Difficulty**: Click on a difficulty level chip (Beginner, Intermediate, Advanced) to load topics
2. **View Topics**: All topics for the selected difficulty will be displayed
3. **Seed Topics**: Click "Seed Beginner Topics" to populate the database with beginner lesson topics
4. **Monitor Status**: Watch for success/error messages at the top of the window

## Requirements

- Same Supabase configuration as main app (uses same `.env` file)
- Supabase RLS policies must allow INSERT/UPDATE for authenticated users (see `FIX_RLS_POLICY_MANUAL.md`)

## Architecture

- **AdminMain.kt**: Entry point for admin application
- **AdminApp.kt**: Main UI composable
- **AdminTopicsList.kt**: List view for displaying topics
- **AdminLessonTopicsViewModel.kt**: ViewModel for managing topic operations
- **AdminPreferencesManager.kt**: Separate preferences manager (isolated from main app)

## Future Enhancements

- Add/Edit/Delete individual topics
- Bulk import/export topics
- Topic validation and testing
- Progress tracking for topics


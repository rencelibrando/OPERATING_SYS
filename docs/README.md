# WordBridge GitHub Pages

This directory contains the static files for the WordBridge web companion, hosted on GitHub Pages.

## Files

- `index.html` - Main landing page
- `auth/callback.html` - Email verification callback page used by Supabase Auth

## Setup Instructions

1. **Enable GitHub Pages**:
   - Go to your repository settings
   - Navigate to "Pages" section
   - Set source to "Deploy from a branch"
   - Select branch: `main` (or your default branch)
   - Set folder: `/docs`
   - Save settings

2. **Update Supabase Config**:
   - In your Kotlin app, update `SupabaseConfig.kt`:
   ```kotlin
   const val EMAIL_REDIRECT_URL: String = "https://your-username.github.io/your-repo/auth/callback.html"
   ```

3. **Configure Supabase Dashboard**:
   - Go to your Supabase project dashboard
   - Navigate to Authentication → URL Configuration
   - Set **Site URL**: `https://your-username.github.io/your-repo`
   - Add to **Redirect URLs**: `https://your-username.github.io/your-repo/auth/callback.html`

## How It Works

1. User signs up in your desktop app
2. Supabase sends verification email with link to `callback.html`
3. User clicks link (on any device) → opens the callback page
4. Callback page shows success message
5. Desktop app detects verification via polling and signs user in

## Customization

You can customize the callback page by editing `auth/callback.html`:
- Change colors, fonts, or styling
- Modify success/error messages
- Add your own branding
- Adjust auto-close timing

The page handles both success and error cases automatically.

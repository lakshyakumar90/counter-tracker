# Supabase development setup

Pace supports an optional account. Local recording, settings, and the Room
database continue to work without a Supabase project or internet access. P06
creates only the account and its profile row; it does not upload fitness data.
Backup and sync belong to P17.

## Create a development project

1. Create a Supabase development project in the Supabase dashboard.
2. In **Authentication → URL Configuration**, allow the callback URL
   `pace://auth-callback`.
3. In **Authentication → Providers → Email**, enable email signup and
   verification. Configure password recovery to return to the same callback.
4. Apply `migrations/20261118000000_profiles.sql` in the project's SQL editor.
5. Copy the project URL and publishable key from the dashboard's Connect panel.
   These are client identifiers, not service-role credentials. Never put a
   service-role or secret key in the Android app.
6. Put these Gradle properties in the user's `%USERPROFILE%\.gradle\gradle.properties`
   (or the equivalent user-level Gradle properties file):

   ```properties
   PACE_SUPABASE_URL=https://<project-ref>.supabase.co
   PACE_SUPABASE_PUBLISHABLE_KEY=<publishable-key>
   ```

7. Run the app's normal code checks. An app built without both properties stays
   in local-only mode and displays that account setup is unavailable.

Do not commit project keys, access tokens, or account data. Create a distinct
production project only when production setup is explicitly scheduled. Before
the P06 gate can close, verify signup, email confirmation, sign-in, password
recovery, session refresh, and profile RLS with two separate test accounts.

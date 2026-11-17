# Localization Baseline

All user-facing app copy belongs in Android string resources. The manifest app label also references `@string/app_name`; Compose UI reads text with `stringResource`. Lint is part of local checks and CI.

## Initial locale

The initial scaffold exposes **English (India), `en-IN`**. This follows the provisional India-first product baseline and is for the early product/testing build; it does not approve a final launch region or store listing. Reconfirm launch regions at the P00 checkpoint before release. `values/strings.xml` remains the fallback resource and `values-en-rIN` contains the reviewed English copy for the selected locale. Add translations only when a launch locale and reviewed translation are available.

Dates, times, and numbers must use locale-aware formatters. Measurement units remain controlled by the user's metric/imperial preference and must not be inferred from the device locale.

When adding user-visible text, add a default resource, add translations for supported locales, run `:app:lintDebug`, and check layout expansion and accessibility on a physical phone.

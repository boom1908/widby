# WIDBY

WIDBY is a local-first native Android streak tracker whose primary experience lives in configurable Jetpack Glance homescreen widgets.

## Run & Operate

- Android app: `gradle :app:assembleDebug` (requires JDK 17 and the Android SDK)
- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- Kotlin, Gradle, Android SDK 35, Jetpack Compose, Jetpack Glance
- Room + WorkManager for local persistence and scheduled auto counters
- pnpm workspaces, Node.js 24, TypeScript 5.9 (workspace support services)
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `app/src/main/java/com/widby/data` — Room entities, DAO, database, and streak rules
- `app/src/main/java/com/widby/widget` — Glance widgets, widget actions, and configuration activity
- `app/src/main/java/com/widby/scheduling` — WorkManager auto-daily and reminder workers
- `app/src/main/java/com/widby/MainActivity.kt` — Compose streak manager and heatmap
- `.github/workflows/android.yml` — debug APK CI build

## Architecture decisions

- The app is native Android only; it does not use Expo, React Native, a web wrapper, or a backend.
- Room is the single local source of truth shared by the app, widgets, and workers.
- Auto counters use a 15-minute WorkManager sweep so each streak can honor its own reset time without relying on launcher update intervals.
- Android launcher long-press is reserved for widget movement/resizing; manual widgets expose an explicit Undo action and auto widgets open a confirmed reset flow.

## Product

- Dark-first streak management list with per-streak accent and icon choices
- Manual tap and auto-daily streak types
- Per-widget labels, widget accent/icon overrides, custom reset times, reminders, and heatmap history
- Small, medium, and large Glance layouts that adapt to available widget width

## User preferences

The user explicitly requested a standard Kotlin/Gradle Android project that can later produce an APK through GitHub Actions CI.

## Gotchas

- A JDK 17 + Android SDK environment is required to assemble the app.
- Notification permission is requested on Android 13+; reminders remain optional and local.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details

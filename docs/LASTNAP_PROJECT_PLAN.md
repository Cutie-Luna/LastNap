# LastNap — project plan and permanent handoff

**Plan date:** 2026-11-05  
**Primary working branch:** `lastnap-clean-foundation`  
**Protected branches:** `master`, `feature/extensions-foundation`  
**Upstream project:** `rikkahub/rikkahub`

This document is the project's durable source of truth. Read it at the start of every new chat before touching the repository. Update it at the end of every work session so that the next chat inherits the full state, decisions, reasons, file changes, test results, blockers, and next actions—not merely a list of checks.

---

## 1. What LastNap is supposed to be

LastNap is a personal version of **RikkaHub**, not a ground-up rewrite and not a redesign of its existing experience.

The intended result is:

1. The original RikkaHub app and behavior remain the foundation.
2. LastNap has its own Android application ID, displayed app name, launcher/notification artwork, and necessary LastNap-specific shortcut identifiers.
3. The agreed new feature set is added in isolated places without changing unrelated RikkaHub screens or behavior.
4. Original navigation, transitions, animations, chat behavior, settings behavior, and performance are preserved unless a specific LastNap feature truly requires a change.
5. The app remains updateable on the user's existing installation, with the same application ID and signing identity.

**Default rule:** if a file does not need to change for LastNap identity, its artwork, the requested feature, or a demonstrated build requirement, leave it byte-for-byte identical to the current upstream RikkaHub baseline.

Do not treat unrelated code as an opportunity to refactor, modernize, optimize, rename, or restyle it.

## 2. Branch safety and current repository state

### Branches

- `lastnap-clean-foundation` was created from LastNap `master` commit `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`.
- `master` is protected: do not modify, reset, force-push, or merge into it without the user's explicit permission.
- `feature/extensions-foundation` is the previous implementation branch. Keep it intact as a historical reference; do not merge it wholesale into the clean branch.
- All new implementation work belongs on `lastnap-clean-foundation`, unless the user explicitly chooses another branch.

### Full-tree baseline audit completed on 2026-11-05

The recursive Git trees were compared, not just selected Kotlin files.

| Snapshot | Files/blobs | Result |
|---|---:|---|
| New clean branch at creation | 1,536 | Same set of paths as the fork's `master` |
| Current upstream RikkaHub master at audit time | 1,536 | Same paths as the clean branch |
| Clean branch vs current upstream | — | Exactly one content difference: `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt` |

The one difference exists because the upstream project received a newer context-compression input change after the fork's `master` commit. It must be reviewed and synchronized as a small, explicit upstream-baseline update during Phase 1; do not silently mix it with LastNap feature work.

### Previous implementation branch audit

At the audit point:

- `feature/extensions-foundation` pointed to `942c1b49019dbbd55f02606f31b74cc258c14641`.
- It was 122 commits ahead of the fork's `master`.
- GitHub's compare endpoint reported 66 changed paths relative to that `master`.
- The recursive tree comparison against current upstream showed 12 LastNap-only paths, 5 upstream paths absent from that branch, and 65 paths with content/presence differences.

This is a broad change set. **Do not copy the branch wholesale.** Review each change and port only what is required by the product requirements below. The old branch remains useful for recovering feature code, artwork, tests, workflow configuration, release setup, and implementation history.

### What differed on the previous branch

The full comparison identified these groups; each file must be reviewed individually before porting:

- App configuration and identity: `app/build.gradle.kts`, `AndroidManifest.xml`, shortcut resources, route handling, and release version/ABI/signing configuration.
- New custom-tool feature files: `CustomExtension.kt`, `CustomExtensionTools.kt`, `ExtensionBuilderPage.kt`, and the built-in `tool-creator` skill.
- Existing feature integration points: assistant model/settings persistence, tool factory/runtime registration, extension content/selector/pages, assistant extension settings, chat UI, and related tests.
- Locale resources: translated strings for the new builder and feature labels.
- Existing RikkaHub file edits: several settings, backup, chat, media, statistics, importer, Mermaid, notification, and provider screens. Some edits were compatibility substitutions or additive UI integrations. They are not automatically required; establish the reason for each before porting.
- Artwork/resources: LastNap cat artwork, notification artwork, adaptive launcher resources, and removal of five old density-specific launcher PNGs on the previous branch.
- Build/release infrastructure: Android verification, signed release, APK signing helper, and daily build workflows.
- Documentation and implementation notes.

The old branch also had navigation transition edits. A previous optimization replaced original slide/scale transitions with fades; that simplification was later reversed. The previous branch's final navigation transitions were restored to RikkaHub's original behavior. The clean branch already starts from the original upstream transition implementation.

## 3. Product requirements to preserve

### A. LastNap identity

- Android application ID: `com.lastnap.app`.
- Displayed app name: `LastNap`, including the supported localized app-name resources where they exist.
- Update path: retain the same application ID and the user's existing signing key.
- The user's installed LastNap 3.1.2 was version code 195. The next compatible personal release was planned as LastNap 3.1.3, version code 196. Confirm the current version values before changing them; never reduce versionCode.
- Update all identity-dependent places that genuinely need to match: Gradle application ID, manifest labels/authorities where applicable, Google Services package configuration, shortcut action identifiers, deep-link schemes, and release metadata.
- Do **not** blindly rename the Kotlin namespace/package `me.rerere.rikkahub`. Android application ID and source namespace are separate concerns. Keep the upstream namespace unless a concrete requirement proves otherwise.
- Search the entire repository for old and new application identifiers before declaring identity work complete. Classify intentional upstream namespace occurrences separately from runtime app identity.

### B. Artwork and icon behavior

- Preserve the user's supplied LastNap cat artwork exactly. Do not redraw, recolor, crop, stylize, or replace it without permission.
- Launcher icon should use Android's adaptive icon resources, with the requested pure black (`#000000`) background and the supplied foreground artwork.
- Preserve a monochrome layer if the chosen artwork supplies one and the resource is supported.
- Keep the separate notification small icon artwork (the previous implementation used `noticon`) rather than accidentally using the colorful launcher icon for notifications.
- Inspect all density-specific and adaptive resource variants together. Resource resolution must be verified; do not delete old resources based on assumption alone.
- Verify the actual packaged APK resource table and, when installation testing is appropriate, the icon shown by Android 13 on the target device.

### C. Custom tools and Tool Builder

The agreed new feature is an assistant-aware custom tool system with a built-in creator skill.

**Assistant-specific assignment**
- Custom tool definitions remain user-level records in Settings.
- Each assistant stores the IDs of custom tools selected for that assistant.
- A tool is callable only when selected for the current assistant and passes the existing enable/test/permission requirements.
- Existing assistants must migrate safely to an empty selection; do not silently expose every custom tool to every assistant.
- Deleting a custom tool must clean up its assistant references safely.
- Keep the current runtime sandbox and permission model. Do not invent browser, filesystem, network, Android, or Node capabilities for the tool sandbox.

**Simple and Advanced modes**
- Simple mode lets the user describe a tool in ordinary language and receive help preparing its name, description, input schema, implementation, and example test input.
- Advanced mode retains direct access to the technical editor, schema, code, test input/results, and permissions.
- Both modes use the exact same underlying saved tool format, sandbox, validation, test-before-enable gate, and permission rules.
- Simple mode must not bypass testing or enable a tool automatically. The user controls saving, assigning, enabling, and permissions.
- A stale test result must never enable edited code. Invalid or oversized input schemas must not become callable tools.

**Tool Builder presentation redesign — UI only**

The current add-tool popup feels cramped. The user wants it to feel more open and less complicated, without making it tiny or removing functionality. This is a presentation/layout redesign only.

Preferred direction for Stage 5:
- Replace the cramped add-tool modal with a dedicated full-screen page or roomy page-like editor route.
- Give it a clear title, short explanation, and a visible Simple / Advanced mode switch.
- Keep Simple and Advanced functionally intact. The mode switch changes presentation and guidance only, never the saved tool model, permissions, sandbox, validation, testing, or enablement rules.
- Use a comfortable vertical flow with grouped sections and generous spacing. Do not dump every technical field onto one screen in Simple mode.
- Simple mode guides the user through the same underlying information with plain-language labels and examples.
- Advanced mode keeps direct access to all technical fields and controls, organized into readable sections.
- Add a Preview / Review step or panel before saving. It summarizes name, description, inputs, and implementation/test status. Preview is informational: it must not execute or enable a tool.
- Keep test input/output, validation errors, permissions, and test-before-enable status visible and understandable.
- On phones, use a single-column scrollable layout with a persistent but unobtrusive bottom action area for valid actions such as Back, Test, and Save. Avoid cramped split panes.
- A side preview on larger screens is optional only if it genuinely improves clarity; mobile is the primary design target.
- Before changing the UI, inspect the existing popup and make a parity checklist of every field, action, validation state, test control, permission, and save/enable behavior. Nothing may disappear.

**Design acceptance:** the page feels open and readable on a phone; Simple and Advanced both remain available; every existing field/action remains reachable; preview is informational only; all current safety gates remain unchanged.

**Tool Creator skill**
- Add the built-in `tool-creator` Agent Skill alongside `skill-creator`.
- It is opt-in and assistant-scoped. It must not be injected globally or made available to unrelated assistants by default.
- It teaches the assistant how to create, configure, test, edit, and troubleshoot tools in the existing Tool Builder.
- The skill should give accurate guidance about the actual sandbox and never claim a tool was tested unless the current implementation's test succeeded.
- A direct Tool Builder entry point from an assistant's custom-tools area is useful, but must not change the normal assistant flow.

### D. Existing RikkaHub experience

Keep the original implementation unless a specific feature integration requires a small, documented change:

- Navigation routes and original slide/scale/fade transition specifications.
- Shared-element / hero animation behavior.
- Chat list and chat screen animation behavior.
- Settings screen structure and interactions.
- Statistics screen behavior.
- Assistant pages and existing tool behavior.
- Existing provider, backup, media, image-generation, and notification behavior.
- Existing localization and accessibility behavior.

Do not remove animations to hide lag. Do not rewrite navigation, move work across threads, change Compose state handling, or alter screen structure without a measured or reproducible reason.

### E. Shortcuts

Where custom launcher shortcuts are included, all action names, manifest intent filters, and route constants must agree. The previous LastNap convention used:

- Camera shortcut URI: `lastnap://shortcut`
- Translator action: `com.lastnap.app.action.TRANSLATE`
- Media-creation action: `com.lastnap.app.action.MEDIA_CREATION`

Verify these against the current upstream source and the actual shortcut resources before porting.

### F. Build, release, and signing

- Preserve upstream R8/release optimization and baseline-profile behavior unless a measured need justifies a change.
- The target phone is ARM64; previous release packaging was ARM64-only to avoid unnecessary ABI output.
- Keep the same private signing key for updates. The JKS and password must never enter Git, artifacts, chat messages, or this document.
- GitHub Actions may refer to repository secrets by name, but never print or store their values.
- The previous workflow used the `KEY_BASE64` and `SIGNING_CONFIG` secrets. The existing local keystore backup is held by the user; do not ask for passwords in chat.
- Signed APKs must be verified with Android `apksigner` before being described as release-ready.
- Release notes, package ID, versionName, versionCode, ABI, APK filename, checksum, and artifact must all describe the same build.
- Never publish a public release automatically. A draft release requires explicit user intent and review.

## 4. Engineering rules — apply in every chat

1. **Plan before edits.** First state the objective, scope, intended files/areas, risks, and acceptance criteria. Update this document before starting a new multi-step task.
2. **Inspect the whole repository.** Read the current branch and commit, fetch the recursive file tree, and compare it with the recorded baseline/upstream. Do not limit the audit to the files already mentioned in a chat summary.
3. **Read before writing.** Fetch the current content of every file that may change and its upstream counterpart. For binary assets, compare blob IDs, dimensions/metadata when possible, and packaged results.
4. **Minimal diffs.** Prefer a small additive file or isolated integration point. Never run broad formatter or replacement operations across unrelated files.
5. **Explain every changed file.** Record its path, what changed, why the change is required, whether it differs from upstream, and how it was checked.
6. **No speculative performance work.** Measure first. Keep performance investigation separate from feature implementation.
7. **Preserve original behavior.** Any change to an existing screen, animation, route, settings flow, data migration, or runtime registration requires a clear reason and a focused regression check.
8. **No silent scope expansion.** Do not add convenient refactors, new dependencies, visual redesigns, or unrelated compatibility changes without explaining why they are necessary.
9. **Test the changed behavior.** Use targeted tests first, then the full relevant test/lint/build pipeline. A green build does not prove device UX or performance.
10. **Be honest about status.** Distinguish source review, unit test, lint, debug build, signed release build, APK verification, installation, and on-device behavior. Never report one as proof of another.
11. **Do not make the user do repository archaeology.** Use repository tools to inspect files, logs, workflow runs, and artifacts directly whenever available. Ask the user only for device-only observations or actions that cannot be performed remotely.
12. **No merges or destructive branch operations** without the user's explicit permission.

## 5. Work plan — one focused stage per summarized chat

A chat may finish a stage early, but should not silently combine stages. If the scope changes, update the plan first.

### Stage 0 — Establish the clean foundation (current chat)

**Goal:** create a safe working branch and a durable plan before implementation.

- [x] Create `lastnap-clean-foundation` from LastNap `master`.
- [x] Leave `master` and `feature/extensions-foundation` untouched.
- [x] Compare complete repository trees against current upstream.
- [x] Commit this project plan and session handoff to the new branch (initial plan commit: `7742b9073370e45658d363d59f3d32628bf4c6bf`).
- [x] Verify the new branch contains only the planned documentation change after the plan commit; the compare returned exactly one added path: this document.

**Acceptance:** the new branch is a clean RikkaHub base plus this project document; no app source, resources, dependencies, or workflows have been changed.

### Stage 1 — Refresh and freeze the upstream baseline

**Completed 2026-11-05**

**Goal:** make the clean branch match the chosen current RikkaHub upstream baseline before adding LastNap code.

- [x] Rechecked upstream default branch: commit `e85c1d901d30fa32d3c21a423432d940d77acddb`, tree `4551b659557a56ade035d97fce62bec18a4199d4`.
- [x] Reviewed the sole source difference in `CompressContextDialog.kt`; it was the upstream manual numeric-entry enhancement for the keep-recent-messages count.
- [x] Synced that file byte-for-byte from upstream into the clean branch. No feature code was ported.
- [x] Compared complete recursive trees after sync: 1,537 files in LastNap (including this plan), 1,536 upstream files, and exactly one path difference: this project plan. No remaining source/resource mismatches.
- [x] Recorded the exact upstream commit and tree SHAs above. Re-check upstream at the start of future stages in case it advances.
- [x] No upstream files intentionally omitted; the only additional path is the LastNap project plan.
- [x] The clean branch inherited original upstream navigation and animation sources; no navigation/animation source was changed in Stage 1.
- [x] No feature code was ported.

**Acceptance:** complete tree comparison is clean against the chosen upstream commit, except for the documented LastNap project plan; baseline SHAs are written here. This acceptance was met on 2026-11-05.

### Stage 2 — Apply LastNap identity and artwork only

**Goal:** make the clean RikkaHub baseline install as LastNap without changing its behavior.

- Audit Gradle identity/version, manifest labels/authorities, app-name strings, shortcut and deep-link identifiers, Google Services configuration, notification references, and release metadata.
- Set the application ID to `com.lastnap.app`; keep the RikkaHub source namespace unless required.
- Set the app display name and all relevant localized app-name strings to LastNap.
- Recover the exact existing LastNap artwork from the previous branch or the user's supplied assets; preserve it byte-for-byte.
- Add the adaptive launcher setup with the pure black background and the correct foreground/monochrome resources.
- Keep the notification icon separate.
- Inspect all five launcher density fallbacks and adaptive resources together. Verify actual resource selection before deciding which legacy files should remain or be removed.
- Audit every occurrence of the old application ID and LastNap application ID; classify source namespace references separately.
- Change only files necessary for identity/artwork and record all of them.

**Initial source-level checkpoint (2026-11-05):** the application ID, version, app labels, launcher/monochrome/notification resource references, shortcuts, About branding, and the eight app-module localized strings were updated; the source diff contains no feature work or unrelated navigation/UI changes. A later review found four RikkaHub references in each OAuth locale, four English fallback strings in `OAuthLoopbackCallbackServer.kt`, and two hard-coded RikkaHub labels in the generated callback page (document title and visible brand label). The OAuth correction is now complete and verified. Stage 2 remains open only for the reliable repository-wide literal search; APK/package-table and device acceptance remains deferred to Stage 8. **Final APK/package-table and on-device icon acceptance remains open for Stage 8** because this clean branch has not yet been built or installed.

### Stage 3 — Design and implement the custom-tool data model

**Goal:** introduce the smallest safe data/storage foundation for assistant-scoped custom tools.

- Inspect the existing assistant model, settings serialization, datastore migration behavior, current tool model, and runtime tool registration.
- Design the persisted custom tool record and assistant-to-tool ID relationship before editing.
- Define migration defaults explicitly; existing assistants must not gain tools automatically.
- Define validation constraints for names, descriptions, schemas, code, and size limits.
- Define deletion cleanup and behavior for missing/invalid tool IDs.
- Add focused unit tests for serialization, defaults, migration, assignment, deletion cleanup, and schema validation.
- Avoid modifying unrelated screens at this stage.

**Acceptance:** data round-trips safely; existing settings load; no custom tool becomes callable merely because it exists.

### Stage 4 — Integrate runtime, sandbox, and safety gates

**Goal:** make selected tools callable only by the correct assistant and only after explicit user approval.

- Trace the complete current tool-registration path from assistant selection to model request.
- Filter custom tool definitions by the current assistant's selected IDs.
- Preserve the current isolated JavaScript sandbox and its real capability limits.
- Preserve test-before-enable and explicit permission approval.
- Reject invalid/oversized schemas and untested or edited-after-test implementations.
- Ensure tool deletion removes stale assignments.
- Add runtime tests for included/excluded assistants, disabled tools, permission handling, failures, and changed-code test invalidation.
- Do not change built-in RikkaHub tools or provider behavior.

**Acceptance:** each assistant receives exactly its selected, valid, enabled, permission-approved custom tools; all other assistants receive none.

### Stage 5 — Redesign the Simple and Advanced Tool Builder UI

**Goal:** keep both existing modes and all functionality, but replace the cramped add-tool popup with a spacious, guided page and an optional review/preview step. This stage is a presentation/layout redesign, not a tool-system rewrite.

- Review the existing Extension Builder implementation from the previous branch before porting it.
- First inventory every current popup field, action, validation message, test control, permission control, and save/enable behavior; make a parity checklist before layout edits.
- Prototype the dedicated full-screen/page-like flow described in the Tool Builder presentation section above.
- Keep Advanced mode faithful to the existing technical editor and its behavior, but organize fields into spacious sections.
- Add Simple mode as a guided natural-language route to the same fields and saved tool format.
- Add a Preview / Review page or panel that summarizes the configured tool without executing or enabling it.
- Keep the mobile layout single-column, comfortably sized, and scrollable; consider a side preview only on larger screens if it helps.
- Both modes must share validation, sandbox, permission, and test-before-enable logic.
- Keep saving, enabling, assigning, and granting permissions under explicit user control.
- Add targeted UI/state tests where the current architecture supports them.
- Verify empty, invalid, oversized, loading, failed-test, stale-test, and successful-test states.

**Acceptance:** Simple and Advanced modes produce the same underlying tool record and neither mode bypasses safety gates. Every existing field/action remains reachable; the new page feels open and readable on a phone; preview is informational only.

### Stage 6 — Add the Tool Creator skill and assistant entry points

**Goal:** integrate the built-in skill into the existing skill and assistant systems.

- [x] Review the existing Agent Skill loading/enablement rules.
- [x] Add `tool-creator` alongside `skill-creator`.
- [x] Keep it disabled until explicitly enabled for the relevant assistant.
- [x] Ensure its instructions are not injected globally.
- [x] Add the assistant-specific custom-tool selection UI.
- [x] Add a direct Tool Builder entry point within the assistant's Custom Tools area.
- [x] Verify assistants without the skill/tools do not receive their instructions or callable definitions.
- [x] Keep roleplay/general assistants unaffected unless the user assigns the feature.

**Acceptance:** source review and focused CI verify that the feature is opt-in and assistant-scoped end to end. Device-level presentation and full-app integration remain Stage 8 verification items; translated strings remain Stage 7.

### Stage 7 — Locales, shortcuts, and integration polish

<badge color="success">Source review and focused CI complete</badge>

**Goal:** finish only the user-visible resource and integration work required by the implemented feature.

- [x] Add the required Custom Tools and Tool Builder strings to all seven supported non-English app locales.
- [x] Verify every one of the 80 new English base keys is present in every supported locale, with no duplicate keys or format-placeholder mismatches.
- [x] Verify launcher shortcut actions, manifest filters, and RouteActivity constants agree.
- [x] Verify app label and adaptive launcher foreground/background/monochrome wiring, round-icon manifest reference, and notification small-icon references.
- [x] Confirm settings backup/restore includes custom-tool definitions and assistant-specific tool assignments.
- [x] Record the only seven locale files changed and why.

**Acceptance:** source-level locale and integration checks passed; the translation change compiled and passed the focused CI tests. Packaged resource-table and on-device icon/runtime checks remain Stage 8 verification items.

### Stage 8 — Full verification and release readiness

**Goal:** prove the feature and the LastNap package can be built and updated safely.

- Run targeted unit tests for every changed subsystem.
- Run the full unit test suite.
- Run Android lint.
- Assemble the debug APK.
- Assemble the optimized signed ARM64 release APK.
- Verify APK package ID, versionName, versionCode, ABI, icon resources, and signing certificate.
- Verify APK signature with `apksigner`.
- Upload test reports, lint reports, APK, mapping file, checksum, and build metadata as appropriate.
- Inspect CI logs and artifacts directly; record workflow run IDs and exact commit SHAs.
- Do not call a release ready until the signed APK is available and verified.
- Do not publish the release; prepare a draft only when the user explicitly asks and review it with them.
- On-device installation/update testing is a separate acceptance item and must be labeled as such.

**Acceptance:** all required CI tasks pass; APK identity/signature/version are consistent; user can review the artifact before installation.

### Stage 9 — Performance investigation (separate from feature work)

**Goal:** understand the reported lag without sacrificing RikkaHub behavior.

Known user observations from the previous build:
- The first opening/use of some animated UI stutters; subsequent use becomes smoother.
- Navigation to some screens, including Settings from the chat drawer and Statistics, feels delayed (roughly two seconds was reported).
- RikkaHub itself appeared smoother on the same itel P55.
- Original animations were restored on the previous branch; do not remove them again as a speculative fix.

Known evidence and limitations:
- `HeroAnimation.kt` and the original shared-element animation implementation matched upstream in the prior parity review.
- `StatsVM` and `SettingPage` matched upstream at that review point.
- `ChatPage` and `StatsPage` had small API/locale compatibility differences.
- Baseline Profile source files were empty in both the previous LastNap branch and upstream at the time of review; their emptiness is not evidence of a LastNap-only regression.
- No Perfetto or Macrobenchmark trace has yet identified the cause on the target phone.
- No measured performance fix has been established.

Procedure:
1. Build comparable optimized release variants from the selected upstream baseline and LastNap branch with matching settings.
2. Record cold startup, first navigation, warm navigation, Settings entry, Statistics entry, and first-use animation behavior on the same target device.
3. Capture Perfetto/system trace and Compose/recomposition evidence where available.
4. Compare main-thread work, frame timing, startup initialization, navigation composition, database work, and blocking I/O.
5. Identify one measured bottleneck and propose the smallest targeted change.
6. Capture the same trace again after the change and compare results.
7. Keep animations and effects intact unless evidence shows a specific one is the source and the user approves the behavior change.

**Acceptance:** a documented before/after measurement on the target device. If no device trace can be collected, report the blocker and do not claim the lag is fixed.

## 6. Previous release and testing history

This history is useful context, not a guarantee that the current clean branch has been tested.

- A LastNap 3.1.2 ARM64 APK was previously built and signed with application ID `com.lastnap.app`, version code 195. Signature verification passed. Its recorded SHA-256 was `fce2bce0b67a565ca8dafb98ea16851f0d092ced10f7a67188b67af200e72d27`.
- The previous personal signing keystore is backed up locally by the user. Preserve it for update compatibility; never copy it into the repository.
- The previous release workflow passed in run 41897065669; the corresponding Android verification run 41897065076 passed at that time.
- Unit tests, debug APK assembly/upload, and lint previously passed in run 41888159799.
- On 2026-11-05, verification run 41989830645 completed successfully for previous-branch commit `942c1b49019dbbd55f02606f31b74cc258c14641`.
- At the same check, release workflow run 41989830683 for that commit was still in progress. Recheck its final status before relying on it.
- Earlier release workflows triggered by intermediate launcher-icon commits were cancelled when newer commits arrived; do not count those cancelled runs as release passes.
- The clean branch has not yet had its own build, test, lint, signed APK, or device installation verification.

## 7. File-change ledger

Update this table at the end of every chat. Add rows rather than erasing previous history.

| Date / chat | Branch and commit | Files added/changed/deleted | Reason and behavior impact | Verification / outcome |
|---|---|---|---|---|
| 2026-11-05 — clean foundation created | `lastnap-clean-foundation`, plan commit `7742b9073370e45658d363d59f3d32628bf4c6bf` (base `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`) | Added `docs/LASTNAP_PROJECT_PLAN.md` only | New clean starting point and permanent project handoff; no app source/resources/workflows changed | Compare against starting commit reported exactly 1 added file, 387 insertions, 0 deletions; no build run because app code was not changed. Full-tree audit: 1,536 paths; one upstream content difference documented above |
| 2026-11-05 — Stage 2 identity/artwork source update | `lastnap-clean-foundation`, source commit `c5bbc0f4424f922cce28a17c4330f861a7a1b093`, localization commit `0020c55a352e8950a3c10fd27cafe9244c1382c5` | Modified `app/build.gradle.kts`, `AndroidManifest.xml`, `RouteActivity.kt`, `SettingAboutPage.kt`, `NotificationUtil.kt`, adaptive launcher XML, shortcuts XML, and all eight supported `strings.xml` files; added `drawable/ic_launcher_background.xml`, `drawable-nodpi/appicon.png`, and `drawable-nodpi/noticon.png`; updated this plan | Set applicationId to `com.lastnap.app`, version 3.1.3/code 196, LastNap app and share/web-server labels, LastNap shortcut scheme/actions, About-page repo/license links, adaptive cat foreground + cat monochrome, pure-black background, and notification cat icon. Kept Kotlin namespace and all unrelated RikkaHub behavior. Two PNG blobs match the old branch byte-for-byte. | Full tree: 1,540 blobs vs 1,536 upstream; 3 intended app assets + plan added, 15 expected existing paths modified, none missing. All eight locale files report app_name LastNap and zero RikkaHub string occurrences. Source refs manually checked. No build, lint, APK resource-table, install, or device verification; full repository-wide code search remains open due connector/network limitation. |
| 2026-11-05 — Stage 2 OAuth branding cleanup | `lastnap-clean-foundation`, final source commit `893ef5215f03b97cb34d31b717f327751c7f7ce6` | Modified `oauth/src/main/java/me/rerere/oauth/OAuthLoopbackCallbackServer.kt` and all eight `oauth/src/main/res/values*/strings.xml` files (base, ar, de, ja, ko-rKR, ru, zh-rTW, zh) | Replaced only the old RikkaHub product-name token in four English fallback hints, the generated callback page title and brand label, and four translated callback hints per locale. No callback behavior, keys, layout, or other wording changed. | Read all nine files after the edits: zero remaining `RikkaHub` literals; Kotlin callback source has six LastNap brand tokens and each OAuth locale has four. No tests/build run; source-only branding correction. |
| 2026-11-05 — Stage 2 identity follow-up (package test, filenames, Bonjour label, foreground icons) | `lastnap-clean-foundation`, final source commit `1e5372f9517f922c51a62df23b162a15f1dcbf0b` | Modified `app/src/androidTest/java/me/rerere/rikkahub/ExampleInstrumentedTest.kt`; `app/src/main/java/me/rerere/rikkahub/utils/ContextUtil.kt`; `app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationCommon.kt`; `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/ImportExportTab.kt`; `app/src/main/java/me/rerere/rikkahub/web/NsdServiceRegistrar.kt`; and `WebServerService.kt`, `MemoryDreamForegroundService.kt`, `MediaCreationForegroundService.kt`, `ChatGenerationForegroundService.kt` | Updated the only app-ID test expectation to `com.lastnap.app`; changed generated image/media/backup filenames to LastNap; changed the Bonjour display label to `LastNap Web Server`; changed all five active small-icon call sites to `R.drawable.noticon`. Left the service hostname, internal service actions, source namespace, and stored-data keys unchanged. | Re-fetched all nine files and confirmed only intended literal substitutions. Diffed the four foreground-service source files against frozen upstream: exactly two, one, one, and one small-icon changes respectively. No build, tests, lint, APK or device verification run. |
| 2026-11-05 — Stage 2 embedded Web UI branding | `lastnap-clean-foundation`, final source commit `50e67bd846b5bb6a89bd9a0a586e37af70dc07f0` | Modified `web-ui/app/locales/en-US/page.json`, `web-ui/app/locales/zh-CN/page.json`, and `web-ui/app/components/conversation-sidebar.tsx` | Updated browser page metadata to LastNap Web / LastNap web client in both available locales, and changed the sidebar footer label and link to the LastNap repository. The Web UI is built and copied into packaged Android resources by `web/build.gradle.kts`; no build logic was changed. | Re-fetched all three files and checked other Web UI brand search hits. The actual RikkaHub search-provider name and internal web-auth/theme storage keys were left intact. No Web UI build or app build run. |
| 2026-11-05 — Stage 2 public README branding | `lastnap-clean-foundation`, final source commit `82aa9acf2d4fd47d39d68591f8a9e4ae551c3d4e` | Modified `README.md`, `README_ZH_CN.md`, and `README_ZH_TW.md` | Rebranded the public repository landing pages to LastNap, reused the exact cat launcher asset, marked upstream RikkaHub download/community/sponsor/star-history references clearly, and linked the LastNap Releases page while noting no release is currently published. | Re-fetched all three files; checked title, icon reference, release link, and retained upstream links. No source/build files changed in this pass. |
| 2026-11-05 — Stage 2 export attribution and LastNap update source | `lastnap-clean-foundation`, final source commit `b72dd93777f4450affbda28c3b80537663b1d2b2` | Modified `Mermaid.kt`, `ai/.../Request.kt`, `OpenRouterMediaGenerationProvider.kt`, and `UpdateChecker.kt` | Changed Mermaid export watermark to LastNap; changed OpenRouter X-Title and HTTP-Referer attribution to LastNap; replaced upstream updater URL and response parser with public LastNap GitHub Releases list, stable published APK filtering, asset mapping and human-readable sizes. No-release state maps to current version so no false update card appears. | Re-fetched all files; verified zero `RikkaHub` or `rikka-ai.com` literals in these five recently updated source files (including ConversationExport). Compared the four small attribution files with upstream and confirmed only the planned replacements; UpdateChecker full source reviewed. Public LastNap releases API returned an empty list. No build/tests run. |
| 2026-11-05 — Gradle root project identity | `lastnap-clean-foundation`, source commit `2c91e23329f52b755ec936cd499ab103386b38cf` | Modified `settings.gradle.kts` | Changed only `rootProject.name` from `rikkahub` to `LastNap`; no module includes, dependencies, packages, or build logic changed. | Re-fetched the file after commit; exact intended root project display name confirmed. No Gradle configuration/build executed. |
| 2026-11-05 — Stage 2 identity follow-up (network/MCP defaults, OpenRouter test, lowercase locale text) | `lastnap-clean-foundation`, source head `51fbe308ba51b56f6d1ff65134d7d383ad2161e8` | Modified `app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt`, `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesNetworkPage.kt`, `app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthCoordinator.kt`, `mediagen/src/test/java/me/rerere/mediagen/provider/providers/openrouter/OpenRouterMediaGenerationProviderTest.kt`, and all eight app locale `strings.xml` files | Changed the default network User-Agent and its settings placeholder to `LastNap-Android/<version>`; changed only the blank-name MCP dynamic-registration client name to `LastNap`; updated the OpenRouter media test to expect `X-Title: LastNap`; changed the ASR timestamp help-string app-name token from lowercase `rikkahub` to `LastNap` in eight translations. Existing saved custom User-Agent values, explicitly named MCP servers, upstream MCP client metadata URL, provider resource IDs, and other translations remain unchanged. | Re-fetched all 12 files and verified exact values. Case-insensitive scan: eight app locales now retain only the intentional `rikkahub_provider_description` resource key; all eight OAuth and all 16 Search/UI locale strings contain zero `rikkahub` references. Test expectation matches production OpenRouter header. No build/tests executed. |
| 2026-11-04 to 2026-11-05 — previous implementation | `feature/extensions-foundation`, audit head `942c1b49019dbbd55f02606f31b74cc258c14641` | 66 changed paths relative to old fork master; detailed GitHub compare remains available | Prior identity, custom-tool work, UI integration, assets, workflow, locale and compatibility changes; must be selectively reviewed before porting | Earlier CI runs passed as listed; latest release run still needed recheck; no measured device performance result |

| 2026-11-05 — Stage 7 Custom Tools / Tool Builder localization | `lastnap-clean-foundation`, commit `65de876e5d4cc9d2f49facc2075ff0f1485d3066` | Modified only `app/src/main/res/values-ar/strings.xml`, `values-de/strings.xml`, `values-ja/strings.xml`, `values-ko-rKR/strings.xml`, `values-ru/strings.xml`, `values-zh-rTW/strings.xml`, and `values-zh/strings.xml` | Added the 80 new English-base Custom Tools, assistant selector, and Builder strings to each supported locale. Reused historical wording only where it matched current behavior; updated stale UI wording for the current Tool Creator, Simple/Advanced AI help, and single Need approval switch. English base strings and all unrelated locale strings were left unchanged. | Verified all 80 keys in all seven locales, no missing/duplicate keys, identical format placeholders, and no raw XML ampersands in the added translations. Shortcut/manifest/RouteActivity action IDs agree; adaptive icon uses appicon, black background and noticon monochrome; active notification builders use noticon. Settings backup always serializes `settings.json`, including `customExtensions` and assistants with `selectedCustomExtensionIds`; existing SettingsFieldsTest exercises the round trip and legacy defaults. CI run [42057972085](https://github.com/Cutie-Luna/LastNap/actions/runs/42057972085) passed resource merge, resource processing, Kotlin compile and the eight focused test classes; `BUILD SUCCESSFUL in 4m 6s`. No full lint/suite/APK/signature/device test was run. |

### Per-chat handoff template

Every new chat must append a filled handoff using this structure:

- **Date and objective:**
- **Branch and starting commit:**
- **Plan stage:**
- **Repository audit:** file-tree counts, upstream SHA, added/deleted/renamed/modified paths, and any unexplained mismatch.
- **Files changed:** for every file, state what changed and why it was necessary. Include binary assets and workflows.
- **Decisions:** what was chosen, what was rejected, and the reason.
- **Tests/checks:** exact command or workflow, run ID, commit SHA, and pass/fail/not-run status.
- **Artifacts:** exact artifact names, APK metadata/checksum/signature when applicable.
- **Device observations:** what was actually observed and by whom; do not infer.
- **Known issues and blockers:**
- **Next chat:** one focused objective, intended files/areas, risks, and acceptance criteria.

At the start of the next chat:
1. Read this whole document.
2. Query the current branch, HEAD, recent commits, and workflow status.
3. Fetch the complete recursive repository tree and compare against the recorded baseline and live upstream.
4. Review all changed paths and relevant file contents before editing.
5. State the stage plan, then proceed only within that scope.

At the end of the chat, update this document before presenting the handoff summary. Never leave important decisions, reasons, or failed attempts only in conversation history.


## 44. Stage 7 localization and integration polish — source review and focused CI complete (2026-11-05)

### Locale implementation

- Final source commit: `65de876e5d4cc9d2f49facc2075ff0f1485d3066` on `lastnap-clean-foundation`.
- Changed exactly seven app locale resource files: `values-ar`, `values-de`, `values-ja`, `values-ko-rKR`, `values-ru`, `values-zh-rTW`, and `values-zh`, each in its `strings.xml`.
- Added all 80 current English-base keys for Custom Tools, Tool Builder, assistant-specific selection, test/enablement states, approval-switch copy, and global Extensions navigation.
- Older translations were reused only where the semantics still matched. Obsolete language about AI assistance being a future feature, four permission choices, or generic extension records was replaced with copy matching the current Tool Creator workflow and single **Need approval** switch.
- Post-commit validation fetched all seven files again. Each locale contains 80/80 keys; no missing keys or duplicate resource names; all format-placeholder signatures match the English base. The added translation values have no raw XML ampersands. Two pre-existing `&amp;amp;` values in Arabic/Korean strings were compared against upstream and are identical upstream values; they were not changed as unrelated work.
- No English base resource, OAuth/Search/Web UI locale, or non-locale source file was changed in the translation commit.

### Integration and persistence review

- Shortcut and intent consistency reviewed across `AndroidManifest.xml`, `res/xml/shortcuts.xml`, and `RouteActivity.kt`: the `lastnap://shortcut` camera route and the `com.lastnap.app.action.TRANSLATE` / `com.lastnap.app.action.MEDIA_CREATION` actions match. The shortcut handler's camera/share behavior remains unchanged.
- Adaptive launcher configuration reviewed: `ic_launcher.xml` uses the existing cat `appicon` foreground, solid-black `#000000` background, and `noticon` monochrome layer. The manifest uses the adaptive resource for both launcher and round icons. This is source-level wiring only; packaged resource resolution still requires Stage 8.
- Notification small-icon references reviewed: the shared notification default and all four foreground-service notification builders use the separate `noticon` resource.
- Settings backup/restore review: `BackupManager.createBackup` always writes a complete `settings.json` alongside optional database/attachment entries. Its serialized `Settings` object contains `customExtensions` and the `assistants` list; `Assistant.selectedCustomExtensionIds` is serialized as part of each assistant. Restore migrates/decodes the complete Settings payload and applies it through `SettingsStore.restoreBeforeInitialization` before Koin and data consumers start. No backup-format change was required.
- Existing `SettingsFieldsTest` sample contains a custom-tool record and an assistant selection, and its preferences round-trip test plus legacy-default tests cover persistence behavior. The same test class passed in the Stage 7 CI run.

### Focused verification

- CI run: [GitHub Actions run 42057972085](https://github.com/Cutie-Luna/LastNap/actions/runs/42057972085), branch `lastnap-clean-foundation`, head `65de876e5d4cc9d2f49facc2075ff0f1485d3066`.
- The `persistence-tests` job and “Run focused custom-tool model and runtime tests” step both completed successfully.
- Logs confirm `:app:mergeGithubDebugResources`, `:app:processGithubDebugResources`, `:app:compileGithubDebugKotlin`, and `:app:testGithubDebugUnitTest`; Gradle reported `BUILD SUCCESSFUL in 4m 6s`.
- Eight focused test classes ran: `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, `SettingsStoreTest`, `CustomExtensionToolsTest`, `ExtensionBuilderStateTest`, `BuiltinSkillsTest`, and `SkillsToolsTest`.
- This was not the full unit-test suite, lint, a complete debug/release APK build, signing verification, installation, or a device test. Do not infer packaged icon/resource selection or phone layout from this CI result.

### Stage 7 disposition and next stage

- **Stage 7 source implementation, resource validation, integration review, and focused CI acceptance: complete.**
- **Stage 8 is next:** run the full relevant test suite and lint, assemble the debug APK, produce the optimized ARM64 signed release APK using the user's existing signing configuration without exposing secrets, inspect package/version/ABI/manifest/resource-table metadata, and verify the APK signature. Then present the build for the user's Android 13 device review. Keep installation/device claims separate from CI.


## 8. Current next action

Stages 0–7 are complete at their documented source-review and focused-CI acceptance levels. Stage 7 finished on `65de876e5d4cc9d2f49facc2075ff0f1485d3066`; the locale strings, shortcuts, manifest/RouteActivity action consistency, icon references, and settings backup inclusion have been reviewed and the focused resource/compile/test workflow passed (Section 44).

**Next: Stage 8 — full verification and release readiness.** Run the full unit-test suite and Android lint, assemble the debug APK, assemble the optimized signed ARM64 release APK using the user's already-held signing configuration, and verify package ID `com.lastnap.app`, versionName/versionCode, ABI, manifest, packaged launcher/round/monochrome/notification resources, and APK signature with `apksigner`. Never expose or commit signing material. Do not publish a release. After a real build is available and inspected, ask the user to perform the Android 13 install/update and visual/runtime checks; no phone action is needed before that build exists.

The clean branch has passed focused CI only so far. No full test suite, lint, complete debug APK build, signed release APK, package/resource-table inspection, signature verification, installation, or device check has yet been completed on this branch. Keep each of those checks explicitly open until directly verified.

**Standing instruction for every future chat and every meaningful checkpoint:** read this entire plan first; inspect the current branch/commit; compare the full recursive file tree against the frozen/current upstream baseline; inspect all changed files and relevant upstream counterparts; update this plan's ledger and handoff with every file change, decision, test result, failure, blocker, and next action. Do not mark work complete based on assumptions. Keep each stage focused and reasonably sized; do not turn every stage into a full-day project. If something cannot be verified, say so and leave it open rather than skipping it.
## 9. Session handoff — 2026-11-05 (Stage 1)

- **Objective:** clarify the future Tool Builder UI redesign and continue from the clean foundation into upstream baseline synchronization.
- **Branch:** `lastnap-clean-foundation`.
- **Starting commit:** `58545d1aca7dbacb78f5d731178e716006217267`.
- **Ending source commit before this plan update:** `c6d8164333a4a40fa0a4229ff172cb23d930c560`.
- **Upstream baseline:** `rikkahub/rikkahub` master commit `e85c1d901d30fa32d3c21a423432d940d77acddb`; tree `4551b659557a56ade035d97fce62bec18a4199d4`.
- **Repository audit:** full recursive tree compared. Before synchronization there was exactly one source mismatch, `CompressContextDialog.kt`, plus the project plan path. After synchronization there are no source/resource mismatches; only the plan document is LastNap-specific.
- **Files changed:** `app/src/main/java/me/rerere/rikkahub/ui/components/ai/CompressContextDialog.kt` was synced to the latest upstream version. The change adds direct numeric keyboard entry for the recent-message count while retaining increment/decrement controls. This restores baseline parity; it is not a LastNap feature or redesign.
- **Plan changes:** added the user's Tool Builder UI request and design acceptance criteria; expanded Stage 5 to specify a roomy full-screen page, mode switch, grouped fields, preview/review step, mobile-first layout, and strict preservation of all current actions and safeguards. Marked Stage 1 complete and set Stage 2 as next.
- **Tests/checks:** GitHub tree comparison confirms no remaining code/resource mismatch against upstream. No Android build was run because this stage only synchronized a file directly from upstream; build verification remains part of Stage 8.
- **Unverified:** no UI prototype or app build exists yet for the Tool Builder redesign. Do not imply that the popup has already changed.
- **Next chat:** Stage 2 only—identity and artwork audit/port. Begin by reading this whole plan and checking the full repository tree and upstream HEAD again.

## 10. Stage 2 execution notes — 2026-11-05 (identity/artwork only)

### Pre-edit audit and decisions

- Re-read this complete plan (432 lines), verified working branch `lastnap-clean-foundation` at `bd385d9f74a38d6eb1e61b1daae7aa3bb96f72bb`, and compared the full recursive tree with frozen upstream `e85c1d901d30fa32d3c21a423432d940d77acddb`. Result: 1,536 upstream blobs match exactly; the only additional path is this plan.
- Reviewed the previous implementation branch at `942c1b49019dbbd55f02606f31b74cc258c14641` as a source reference only; it remains untouched and will not be merged.
- Current clean baseline uses `applicationId = "me.rerere.rikkahub"`, versionName `3.1.1`, versionCode `194`. The prior installed LastNap was 3.1.2/code 195; the planned next compatible personal build is 3.1.3/code 196. Stage 2 will use `com.lastnap.app`, 3.1.3, and 196, while leaving Kotlin `namespace = "me.rerere.rikkahub"` unchanged.
- The existing `FileProvider`, documents-provider, and AndroidX startup authorities use `${applicationId}` placeholders, so they will resolve to the new application ID without hard-coded authority edits.
- No `google-services.json` file exists in the clean branch tree; there is no checked-in Firebase package file to modify. Confirm any external Firebase configuration during release verification if needed.
- Base English `app_name` is currently RikkaHub. The seven localized resource files also still say RikkaHub. The previous branch changed only the English value; this stage will set the app-name resource to LastNap in all eight supported string files, without copying the previous branch's unrelated added translations.
- The previous branch added the exact user cat art as `app/src/main/res/drawable-nodpi/appicon.png` and separate notification artwork as `noticon.png`. The latter is already referenced by the old branch's NotificationUtil. Both binary files will be copied byte-for-byte.
- Important defect found in the previous icon wiring: its adaptive launcher XML still points to the upstream RikkaHub bunny foreground and monochrome resources, even though the LastNap cat artwork exists. Stage 2 will explicitly wire `appicon` as the adaptive foreground, `noticon` as the monochrome layer and notification small icon, and use the existing pure-black shape background. No artwork will be redrawn, recolored, cropped, or regenerated.
- The app's About page obtains its visual logo from `R.mipmap.ic_launcher`, so correcting the adaptive launcher resource also corrects that in-app logo. Its visible title is hard-coded as RikkaHub; Stage 2 will change that title to LastNap and point the GitHub/license entries to the LastNap repository.
- The manifest and static shortcut XML both contain the old scheme/action identifiers. The previous branch's RouteActivity contains the matching LastNap action constants; only those two constants will be ported, not its unrelated route additions or custom-tool feature code.
- Keep the five density-specific legacy `ic_launcher.png` resources in place for now. The app's minSdk is 26 and Android 26+ selects the `mipmap-anydpi-v26` adaptive resource. Removing fallbacks is unnecessary for this stage and would add risk without improving the target Android 13 device. Their selection will be checked in packaged resources when a build is available.
- Keep the existing upstream source namespace, class names, internal theme resource names, providers, services, and unrelated RikkaHub behavior untouched.
- A follow-up scan of every supported strings.xml found two additional user-facing product-name references per locale: the share-app description and the web-server description. Stage 2 will change those visible brand mentions to LastNap and point the share text to the LastNap repository; this does not rename internal source namespaces or upstream attribution.

### Stage 2 checkpoint list

- [x] Read full plan and verify branch/HEAD.
- [x] Re-compare complete LastNap and frozen-upstream recursive trees.
- [x] Audit current and previous-branch identity/build/manifest/shortcut/icon resources.
- [x] Inspect the exact cat launcher and notification artwork; identify the incorrect adaptive-icon foreground mapping.
- [x] Apply only the identity, app-name, About-page, shortcut, notification, and launcher-resource changes listed above.
- [x] Review the exact diffs for all modified identity/source paths against the frozen baseline; changes are limited to the intended identity/resource edits.
- [x] Verify the resulting complete recursive tree and source resource references; no upstream paths are missing and no unexpected paths differ.
- [x] Review all 32 tracked app/OAuth/Search/UI `strings.xml` files and correct all user-facing OAuth callback brand references, including English fallback and generated HTML labels; verify zero remaining literal `RikkaHub` strings in the eight OAuth locale files and callback source.
- [x] Correct the sole app-level instrumentation assertion to expect `com.lastnap.app` while retaining its Kotlin namespace; leave library-module test package assertions unchanged.
- [x] Align user-facing exported image/media/backup filenames and the Bonjour service display label with LastNap; retain the `rikkahub.local` hostname and stored-data compatibility identifiers.
- [x] Audit every `ic_stat_rikkahub` call site and replace all five active notification-builder references (shared NotificationUtil plus four foreground-service builders) with the dedicated LastNap `noticon` asset; verify zero remaining active-source references.
- [x] Review the embedded Web UI page metadata and footer; update both available locale metadata files and the footer label/link to LastNap while preserving the actual RikkaHub search-provider label and internal Web UI storage keys.
- [x] Rebrand the three root README language variants with LastNap identity and exact cat app icon; clearly label original RikkaHub downloads, community links, sponsors, and star-history as upstream, and state that LastNap has no published release yet.
- [x] Change the Mermaid image-export watermark and both OpenRouter attribution header pairs to LastNap identity.
- [x] Migrate automatic update checks away from the RikkaHub updater to the public LastNap Releases API; map stable published APK assets into the existing update UI and suppress update cards when no LastNap APK release exists.
- [x] Set `rootProject.name = "LastNap"` in root `settings.gradle.kts`; preserve project includes, module names, Kotlin namespace and source paths.
- [x] Complete the source identity audit using a full recursive path/blob comparison, direct review of all 49 modified upstream paths, all 32 tracked app/OAuth/Search/UI string-resource files, all Android XML/manifests, and targeted searches for legacy brand/package/action/scheme/icon/updater/User-Agent identifiers. No known user-facing LastNap identity surface remains unintentionally branded RikkaHub. **Bounded-scan note:** the 1,453 text-like blobs were not all individually opened; 1,491 shared paths are byte-identical to frozen upstream, and all differing paths plus all identity-bearing resource surfaces were reviewed. This limitation is recorded, not represented as an exhaustive text scan. The user authorized proceeding to Stage 3 on this evidence; treat the residual bulk scan as a supplemental audit, not a source-stage blocker.
- [ ] Build and inspect the APK's merged manifest/resource table, then install on Android 13 to verify launcher, round/monochrome, and notification icon behavior. Deferred to Stage 8; no build or device test has been run on this branch.


## 11. Session handoff — 2026-11-05 (Stage 2 identity/artwork)

- **Objective:** apply LastNap package identity and exact existing artwork to the clean upstream baseline, without porting the custom-tool feature or unrelated prior-branch edits.
- **Starting branch/commit:** `lastnap-clean-foundation`, `bd385d9f74a38d6eb1e61b1daae7aa3bb96f72bb`.
- **Intermediate documentation commits:** `c9fbdc918f9e99a6d860ee795a635be2114e26b9` recorded the pre-edit audit; `28908708b4ed4b2ada35163abcd0df2d9eab06e0` recorded the two additional user-visible RikkaHub string references found during review.
- **Source commits:** `c5bbc0f4424f922cce28a17c4330f861a7a1b093` applied package/manifest/routes/about/notification/launcher/shortcut and initial locale changes; `0020c55a352e8950a3c10fd27cafe9244c1382c5` completed LastNap branding in all eight localized string files.
- **Upstream baseline:** live upstream `rikkahub/rikkahub` master remains `e85c1d901d30fa32d3c21a423432d940d77acddb`, tree `4551b659557a56ade035d97fce62bec18a4199d4`.
- **Final source-tree audit before this handoff edit:** 1,540 blobs in LastNap and 1,536 upstream; exactly four LastNap-only paths (three new app assets and this plan), 15 modified upstream paths, and zero missing upstream paths. Tree responses were not truncated.
- **Files modified:** `app/build.gradle.kts` (applicationId `com.lastnap.app`, versionName `3.1.3`, versionCode `196`; source namespace retained); `app/src/main/AndroidManifest.xml` (roundIcon, LastNap shortcut actions and deep-link scheme; provider authorities remain applicationId placeholders); `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (only matching translator/media action constants); `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingAboutPage.kt` (visible app name and LastNap GitHub/license links); `app/src/main/java/me/rerere/rikkahub/utils/NotificationUtil.kt` (small icon uses noticon); `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (LastNap foreground/monochrome layers and black background); `app/src/main/res/xml/shortcuts.xml` (LastNap scheme/actions); all eight supported app strings files (app_name, share text, web-server description); added `app/src/main/res/drawable/ic_launcher_background.xml` (solid #000000), `app/src/main/res/drawable-nodpi/appicon.png`, and `app/src/main/res/drawable-nodpi/noticon.png`.
- **Binary asset integrity:** LastNap `appicon.png` blob SHA `2425691729693f27fe07367f29ed032b9e4c5f36` and `noticon.png` blob SHA `edb86d83f69b55d2286c3c6eb2fc983a344623f5`, identical to the exact assets on the previous implementation branch. Neither was transformed.
- **Decisions:** retain source namespace `me.rerere.rikkahub`; keep the five old density-specific launcher PNG fallbacks (minSdk 26; adaptive resource is used on supported Android versions); keep internal theme names, providers, services, and upstream website attribution. Do not port previous-branch release signing/ABI/build changes here.
- **Checks:** reviewed the source commit patches; confirmed the exact package/version, matching manifest/RouteActivity/shortcut action identifiers, matching LastNap deep-link scheme, adaptive-icon resource references, and notification small-icon reference. Verified app_name is LastNap in en/ar/de/ja/ko/ru/zh-TW/zh; each of those files has zero remaining literal RikkaHub brand mentions and its share text points to the LastNap repo. Confirmed no checked-in `google-services.json` exists in the clean tree.
- **Repository-wide search limitation:** GitHub code search returned zero matches with `incomplete_results=true`, which is not a reliable negative result. A local shallow clone/grep was attempted but failed because this environment could not resolve `github.com`. Manual checks of all known identity entry points passed; exhaustive repository-wide text search remains open.
- **Build/CI/device checks:** no build, lint, tests, APK inspection, signature verification, installation, or target-device icon check was run. The clean branch currently has only the scheduled/manual `daily-build.yml` workflow (plus close-blank-issues); no run exists for this branch. That workflow is not triggered by push, and no workflow-dispatch action is available in the current GitHub connector. It also expects `GOOGLE_SERVICES_JSON`, whose secret availability was not checked. Do not report this source update as a verified APK.
- **Stage status:** source implementation is complete. Stage 2 remains open for a reliable full-repository text scan; final package/resource and Android 13 device verification is deferred to Stage 8.
- **Next action:** finish the exhaustive search when a complete repository checkout/search capability is available. Then continue to Stage 3 only after the Stage 2 source audit is fully closed; carry APK/device proof into Stage 8.


## 12. Additional Stage 2 audit — OAuth callback branding (2026-11-05)

- Re-verified branch `lastnap-clean-foundation` at `b8b90177f74ebf74855168627dabab817940d768` before this audit extension. No source edits had been made during the content review.
- Reviewed all 32 tracked `strings.xml` files across the app, OAuth, Search, and UI modules. The eight app-module locale files have `app_name = LastNap`, LastNap share text and web-server descriptions, and zero literal `RikkaHub` occurrences. Search and UI module strings contain no literal `RikkaHub`. The eight OAuth module locale files each still contain four browser callback hints that tell users to return to RikkaHub.
- Traced these OAuth strings to `oauth/src/main/java/me/rerere/oauth/OAuthLoopbackCallbackServer.kt`: the values are rendered in success, error, invalid-callback, and already-handled browser pages. The English fallback for each of those four hints also says RikkaHub.
- The OAuth references are user-facing product branding, not intentional source namespace or internal class names. They were missed by the original Stage 2 inventory.
- **Correction completed:** changed only the product-name token from RikkaHub to LastNap in six user-facing places in `oauth/src/main/java/me/rerere/oauth/OAuthLoopbackCallbackServer.kt` (four English fallback hints, the generated page's HTML title suffix, and its visible brand label) and the four corresponding translated string values in each of the eight OAuth locale files. Preserved every sentence's existing translation, all resource keys, callback logic, and page layout. The source edits are recorded in the 2026-11-05 OAuth branding cleanup handoff below.
- Inspected the 32 non-`strings.xml` XML resources/manifests. The only other RikkaHub-looking source hits were the existing internal application class `.RikkaHubApp` and theme identifier `Theme.Rikkahub`; keep these stable. The manifest, shortcut XML and route constants use matching LastNap action IDs and scheme. The library module manifests have no app-ID overrides.
- Repository tree remains 1,540 blobs vs 1,536 upstream; no upstream paths missing; three artwork assets plus this plan are LastNap-only; 15 existing upstream paths differ at this checkpoint. The full-tree comparison is complete, but a complete contents scan of every tracked text blob is still not claimed.
- Stage 2 remains open. After the OAuth correction, continue with the repository-wide literal scan, then carry APK/resource/device verification into Stage 8.


## 13. Session handoff — 2026-11-05 (Stage 2 OAuth branding cleanup)

- **Objective:** continue the Stage 2 file-by-file identity audit, correct the additional user-facing OAuth brand references discovered, and verify the final source tree without touching unrelated RikkaHub behavior.
- **Branch and final source commit:** `lastnap-clean-foundation`, `893ef5215f03b97cb34d31b717f327751c7f7ce6`. Protected `master` and historical `feature/extensions-foundation` were not modified.
- **Starting point:** branch head `b8b90177f74ebf74855168627dabab817940d768`; plan note commits `dc4488dffc5c599ebdf34409e19fd24d0874cafc`, `dd06f0eae293626c0ddb93d1f0c7e94f2f2d26f8`, and `b311c7d4bb437ecfa4f30552926e9ae56db194aa) recorded the newly discovered OAuth omissions and the exact correction scope before source edits.
- **New source commits (one commit per path):** `5dfd14b3afab843070d3c3594e73595769daed41` (OAuth callback Kotlin), `d32b7ccb075c39190a741d53c3a81949a012872b` (base English), `0ff1cb12a21a429ff461b60c45de0f275aded5a9` (Arabic), `e614cc32baaf23eecafdf807655835062e5e6bdb` (German), `a12ef202e33d06eb3b1d08e4253779cbaf40e85e` (Japanese), `18ee5a66eb35b6aa2224e1ed9fd2768374f483d6` (Korean), `68ea2e8bd838a87d5bf4a1b6c47eb3ccccbfb049` (Russian), `9cd2e0817b154c851f91fde684d501e4b7c64058` (Traditional Chinese), and `893ef5215f03b97cb34d31b717f327751c7f7ce6` (Simplified Chinese).
- **Repository audit:** current recursive LastNap tree is complete and not truncated: 1,540 blobs. Frozen upstream tree is complete and not truncated: 1,536 blobs. Exactly four LastNap-only paths remain (three app artwork resources and this plan); zero upstream paths are missing; 24 upstream paths differ. These 24 are the 15 previously reviewed Stage 2 identity/localization paths plus the nine OAuth corrections recorded above. No unrelated file paths differ from frozen upstream.
- **File review:** reviewed all 32 tracked `strings.xml` files across app, OAuth, Search, and UI modules; all 32 non-`strings.xml` XML resources and Android manifests; every one of the 24 changed source/resource paths; and the daily-build workflow's identity/artifact-related references. The eight app locale files have LastNap app/share/web-server labels and zero `RikkaHub` strings. Search and UI locale files had no old brand literal. The eight OAuth locale files previously had four such occurrences each; these are now all LastNap. The OAuth Kotlin callback also previously had four fallback hints plus two hard-coded generated-page brand/title strings; all six now say LastNap.
- **Internal references intentionally retained:** Kotlin namespace/package `me.rerere.rikkahub`, class `.RikkaHubApp`, theme `Theme.Rikkahub`, source paths, and official upstream website attribution remain unchanged. The old RikkaHub action IDs and `rikkahub://` shortcut URI are absent from the reviewed manifest, RouteActivity constants, and shortcuts resource; the three locations agree on LastNap action IDs and `lastnap://shortcut`.
- **Artwork:** both cat PNG blob SHAs still match the exact previous-branch assets (`appicon.png`: `2425691729693f27fe07367f29ed032b9e4c5f36`; `noticon.png`: `edb86d83f69b55d2286c3c6eb2fc983a344623f5`). Adaptive launcher XML points to appicon/noticon and the pure-black background. All density-specific upstream fallback launcher/notification PNGs remain unchanged.
- **Verification:** after edits, fetched all nine OAuth files again and confirmed `RikkaHub` count zero in each. OAuth Kotlin file has six `LastNap` references; each OAuth locale has four. Final branch head matches the last source commit above. No build, lint, tests, merged manifest/resource-table inspection, APK/signature verification, installation, or device test was performed.
- **Remaining blocker:** a fully exhaustive content scan of every tracked text blob has not been completed. Full tree/hash comparison and focused file-by-file inspection of identity-bearing resources are complete, but the GitHub connector fetches blob contents individually and the local clone/archive route is unavailable in this environment. Do not claim the repository-wide text search is exhaustive; leave the Stage 2 checkpoint open until a reliable full checkout/scan is available.
- **Next action:** finish the exhaustive text scan, classify any additional old-brand occurrences as user-facing versus intentional upstream/source identifiers, then close Stage 2 source audit. Keep APK/package/resource/device verification in Stage 8. No user phone action is needed for the source audit.


## 14. Additional Stage 2 package-ID test review — 2026-11-05

- A source search of the frozen upstream code found one app-level instrumentation assertion that still hard-codes the old runtime package: `app/src/androidTest/java/me/rerere/rikkahub/ExampleInstrumentedTest.kt` asserts `appContext.packageName == "me.rerere.rikkahub"`. Since the app now installs as `com.lastnap.app`, this assertion would fail when that test runs.
- **Planned minimal correction before editing:** change only the expected package-name string in this app instrumentation test to `com.lastnap.app`. Keep its Kotlin package declaration `me.rerere.rikkahub` unchanged because the source namespace remains unchanged.
- The separate `web/src/androidTest/java/me/rerere/rikkahub/web/ExampleInstrumentedTest.kt` assertion checks the Web library test package `me.rerere.rikkahub.web.test`; it is not the app package ID and must remain unchanged.
- This adds one identity-dependent test file to the Stage 2 review. No other exact app-level assertion matching `assertEquals("me.rerere.rikkahub", ...)` was returned by the upstream code search.


## 15. Additional Stage 2 user-visible filename/service-label audit — 2026-11-05

- Reviewed the remaining exact product-brand hits surfaced by upstream source search and fetched each relevant file from the current branch.
- Found user-visible export/default-name strings in `app/src/main/java/me/rerere/rikkahub/utils/ContextUtil.kt` (two `RikkaHub_<timestamp>.png` defaults), `app/src/main/java/me/rerere/rikkahub/ui/pages/mediacreation/MediaCreationCommon.kt` (one `RikkaHub_<timestamp>.<ext>` gallery filename), and `app/src/main/java/me/rerere/rikkahub/ui/pages/backup/tabs/ImportExportTab.kt` (one `rikkahub_backup_<timestamp>.zip` suggested filename). Also found the Bonjour service display label `RikkaHub Web Server` in `app/src/main/java/me/rerere/rikkahub/web/NsdServiceRegistrar.kt`.
- **Planned minimal correction before editing:** change those four user-visible filename prefixes to `LastNap` / `lastnap` and the Bonjour service display label to `LastNap Web Server`. Do not change the service hostname default `rikkahub`, because existing users may have saved `rikkahub.local` links; the display label can change without changing that hostname.
- **Compatibility identifiers intentionally retained:** `rikkahub.preferences` (existing SharedPreferences file), `rikkahub_secret_cipher` (Android Keystore alias protecting existing encrypted values), `rikkahub_backups/` (S3 backup object prefix), `rikkahub_uploads/` (remote file prefix), `rikkahub_workspaces` (persisted document-provider root ID), and the RikkaHub AI-provider/service-specific label. Renaming these is not cosmetic and risks loss of access to stored settings, secrets, remote objects, or persisted document URIs.
- **Completed package-test correction:** `app/src/androidTest/java/me/rerere/rikkahub/ExampleInstrumentedTest.kt` now expects `com.lastnap.app` while retaining source package `me.rerere.rikkahub`; commit `3fac7ac37daef47420945cb6857298f5bae5a3a5`. The separate Web module test package assertion remains unchanged.


## 16. Additional foreground-notification artwork audit — 2026-11-05

- A repository-wide upstream search for `ic_stat_rikkahub` showed four foreground-service notification builders that bypass the shared `NotificationUtil` default and directly set the legacy launcher/provider icon: `app/src/main/java/me/rerere/rikkahub/service/WebServerService.kt` (two notification builders), `MemoryDreamForegroundService.kt`, `MediaCreationForegroundService.kt`, and `ChatGenerationForegroundService.kt`.
- The Stage 2 artwork requirement is to use the separate LastNap monochrome notification artwork, not the colorful launcher icon or the old RikkaHub icon. The exact asset `app/src/main/res/drawable-nodpi/noticon.png` is already added and `NotificationUtil.kt` uses `R.drawable.noticon`.
- **Planned minimal correction before editing:** change only the `setSmallIcon(R.drawable.ic_stat_rikkahub)` references in those four service files to `setSmallIcon(R.drawable.noticon)`. Preserve notification channels, actions, PendingIntents, content, timing, lifecycle, and all service behavior.
- Internal service action constants such as `me.rerere.rikkahub.action.WEB_SERVER_START`, `...MEMORY_DREAM_ACQUIRE`, `...MEDIA_CREATION_ACQUIRE`, and `...CHAT_GENERATION_ACQUIRE` are private intra-app service commands in existing source files; they are not launcher shortcuts or exported deep links. Keep these stable along with the source namespace. The two externally routed shortcut actions remain the explicitly matched LastNap values in the manifest, shortcuts XML, and `RouteActivity`.


## 17. Session handoff — 2026-11-05 (Stage 2 identity follow-up and notification artwork)

- **Objective:** continue the detailed Stage 2 identity audit, correct further user-facing LastNap references and all remaining old notification icon call sites, while preserving internal package and stored-data compatibility.
- **Branch and code head:** `lastnap-clean-foundation`, final source commit before this plan update `1e5372f9517f922c51a62df23b162a15f1dcbf0b`. The branch head was verified to match this source commit. `master` and `feature/extensions-foundation` were not modified.
- **Pre-edit plan/audit commits:** `0997ba60c25c312cb530f35ae439fd09f9eda1ae` recorded the foreground-notification icon call sites before correction; the earlier plan entries record the OAuth, package-test, and filename/service-label audits.
- **Package test:** `app/src/androidTest/java/me/rerere/rikkahub/ExampleInstrumentedTest.kt` now asserts `com.lastnap.app`; its Kotlin package declaration remains `me.rerere.rikkahub`. The Web module test checks its own `.web.test` ID and remains unchanged. Source commit: `3fac7ac37daef47420945cb6857298f5bae5a3a5`.
- **User-visible generated names:** `ContextUtil.kt` now defaults to `LastNap_<timestamp>.png` for both image export helpers (commit `7174f6e567e1f82e5fcfc043f1459d61535668b0`); `MediaCreationCommon.kt` uses `LastNap_<timestamp>.<ext>` for gallery output (commit `0feacfff31dc824c070d4232e41a2bab0a19461e`); `ImportExportTab.kt` suggests `lastnap_backup_<timestamp>.zip` (commit `d813d02e5f221ead2e5d4a08bbaad55b11bb836b`). These changes affect suggested/exported filenames only; S3 backup prefixes and stored backup directories remain unchanged.
- **Bonjour display label:** `NsdServiceRegistrar.kt` now advertises the display label `LastNap Web Server` (commit `bb67604c94c3edcafe256c481ee2a6d271cbd83f`). The service hostname default `rikkahub` is intentionally retained so existing `rikkahub.local` links continue to resolve.
- **Notification icon call sites:** changed `WebServerService.kt` (two builders), `MemoryDreamForegroundService.kt`, `MediaCreationForegroundService.kt`, and `ChatGenerationForegroundService.kt` to use `R.drawable.noticon`. Source commits: `87d286b25dd6fd74f0ac06be709b9f84d59c40bc`, `84d3ea1a3c538d20f1e4c416ce51ee7493f84090`, `02b21cf6602d1edcad397b81f9242ed5b2211ddd`, and `1e5372f9517f922c51a62df23b162a15f1dcbf0b`. Post-edit file reads confirmed zero remaining `R.drawable.ic_stat_rikkahub` references in the five active notification-builder files (including the already-corrected `NotificationUtil.kt)); counts of `noticon` references are 1, 2, 1, 1, and 1 respectively. Diff review against frozen upstream showed only the intended icon substitutions in the four foreground-service files.
- **Identity/action review:** manifest, shortcuts XML, and `RouteActivity` contain matching LastNap shortcut actions and `lastnap://shortcut`. Private service actions using the old namespace prefix are intra-app commands and are not exported launcher/deep-link actions; they remain unchanged. The Kotlin namespace, class/theme identifiers, preferences file, Keystore alias, remote backup/upload prefixes, workspace document-provider root ID, official upstream site attribution, and upstream RikkaHub provider branding remain intentionally compatible/attributed.
- **Final code tree audit at source HEAD:** full recursive Git trees are complete/non-truncated. LastNap has 1,540 blobs and frozen upstream has 1,536; four LastNap-only paths (three artwork resources plus this plan), zero missing upstream paths, and exactly 33 modified upstream paths. The 33 comprise the original 15 Stage 2 paths, nine OAuth callback branding paths, one app instrumentation test, four generated-filename/Bonjour-label paths, and four foreground-service notification files. No unrelated paths were changed.
- **File coverage:** reviewed all 32 tracked `strings.xml` files across app/OAuth/Search/UI; all 32 non-`strings.xml` XML resources and manifests; all 33 source/resource paths that differ from frozen upstream; the app build identity; relevant notification builders; and the daily-build workflow’s package/artifact/secrets references. Search/UI locale files had no app-brand literals. All eight app locale files and all eight OAuth locale files now have zero literal `RikkaHub` references. The OAuth callback source has zero `RikkaHub` literals. This is a focused audit of changed paths and identity-related resources, not a claim that every one of the repository’s 1,453 text blobs has been individually opened and read.
- **Remaining blocker:** repository-wide exhaustive text-content search across every tracked text blob is not yet proven complete. GitHub code search results are limited/indexed and local checkout/archive access was unavailable in this environment. Targeted searches against the frozen upstream plus full tree/blob-SHA comparisons found and fixed the identified omissions, but Stage 2 source audit remains open until a reliable full checkout or equivalent complete content scan is possible.
- **Tests/build/device:** no app unit/instrumentation tests, lint, debug/release build, merged-manifest or APK resource-table inspection, APK signature verification, installation, or Android 13 device icon check has been run for this clean branch. The app instrumentation assertion was corrected but not executed. Those packaging/device checks remain assigned to Stage 8.
- **Next action:** finish a reliable complete tracked-text scan, classify any further old brand/package/action references, and close Stage 2 source audit. Only then proceed to Stage 3. Do not port custom-tool code yet. No phone action is currently required.


## 18. Additional embedded Web UI branding audit — 2026-11-05

- A narrower upstream code search for user-facing `RikkaHub` text (excluding the large set of internal namespace imports) surfaced three embedded Web UI brand locations: `web-ui/app/locales/en-US/page.json` and `web-ui/app/locales/zh-CN/page.json` set the browser page title/description to RikkaHub Web, and `web-ui/app/components/conversation-sidebar.tsx` displays a RikkaHub footer link.
- Confirmed these files are part of the actual app build: `web/build.gradle.kts` runs the Web UI build and copies its static output into the Android library's packaged resources during `preBuild`.
- **Planned minimal correction before editing:** update the two localized metadata titles/descriptions to LastNap Web / LastNap web client, and change only the footer's displayed name and destination to the LastNap GitHub repository. Preserve the upstream Rikka AI website attribution in the dedicated About-page website entry.
- Only two page-metadata locale files exist in the tracked Web UI locale tree (en-US and zh-CN). Internal Web UI storage identifiers such as `rikkahub:web-auth` and `rikkahub-custom-theme`, the actual RikkaHub search-provider label, root Gradle/plugin identifiers, and historical project/source references are not app-brand display labels and must remain unchanged.


## 19. Session handoff — 2026-11-05 (Stage 2 embedded Web UI branding)

- **Objective:** inspect embedded Web UI branding surfaced by the narrowed search and align its actual user-facing labels with LastNap without altering the separate RikkaHub provider identity or persistent Web UI data keys.
- **Branch and source head:** `lastnap-clean-foundation`, source commit `50e67bd846b5bb6a89bd9a0a586e37af70dc07f0`; verified branch HEAD matched this commit before the handoff plan update.
- **Pre-edit review:** `web/build.gradle.kts` confirms `preBuild` runs the Web UI build and copies its output into the Android `web` module's packaged static resources. The tracked Web UI has exactly two locale `page.json` files: en-US and zh-CN.
- **Files changed:** `web-ui/app/locales/en-US/page.json` changed title/description from “RikkaHub Web” / “RikkaHub web client” to “LastNap Web” / “LastNap web client” (commit `dbb2e1647b149303652afe017617a66da20a3c72`); `web-ui/app/locales/zh-CN/page.json` changed the same title and its Chinese description to LastNap (commit `5f05759d831ce32e7b96fa7cb1e298e2d6147d61`); `web-ui/app/components/conversation-sidebar.tsx` changed the footer label to “LastNap” and its destination from the upstream site to `https://github.com/Cutie-Luna/LastNap` (commit `50e67bd846b5bb6a89bd9a0a586e37af70dc07f0`).
- **Search classification:** a narrow frozen-upstream search found these three visible Web UI brand locations. It also found the search picker label “RikkaHub”, which names the actual RikkaHub search provider and was intentionally retained. Internal keys such as `rikkahub:web-auth` and `rikkahub-custom-theme`, Web UI types/paths, docs, and source identifiers were not altered. The dedicated About-page upstream website attribution remains intact.
- **Final code-tree comparison at source HEAD:** complete/non-truncated recursive trees: 1,540 LastNap blobs vs 1,536 upstream blobs; four LastNap-only paths (the three artwork resources and project plan); zero missing upstream paths; 36 modified upstream paths. The 36 consist of 15 original identity/localization paths, nine OAuth files, one instrumentation test, four generated-filename/Bonjour-label files, four foreground-service notification files, and three Web UI branding files. No other paths differ from frozen upstream.
- **Still open:** exhaustive content-level scan of every tracked text blob is not complete. The repo tree/blob-SHA comparison and focused audit of all changed paths plus all app/OAuth/Search/UI strings, Android manifests/XML, Web UI brand hits, and package/shortcut/icon/service references have been completed. GitHub code search remains indexed/limited, and a complete local checkout/archive could not be obtained in this environment. Do not mark Stage 2 source audit closed until the full text scan is proven.
- **Build/test status:** no Web UI build, Gradle build, unit/instrumentation test, lint, APK manifest/resource-table inspection, signature verification, install, or Android 13 device icon check has been performed on this clean branch. Keep those checks in Stage 8.
- **Next action:** continue with a reliable complete tracked-text scan and classify remaining identifiers as visible brand versus intentional compatibility/provider/source references. Do not begin Stage 3 until Stage 2 is closed.


## 20. Additional public README identity audit — 2026-11-05

- Reviewed `README.md`, `README_ZH_CN.md`, and `README_ZH_TW.md`. Their headings still name RikkaHub, use the old documentation icon, direct download links to the original RikkaHub website/Play Store application ID, and reproduce an upstream fork disclaimer. Their Discord/QQ links, sponsor list, and star-history chart also belong to the upstream RikkaHub project.
- The LastNap GitHub Releases API currently reports no published releases. Therefore, **planned minimal correction before editing:** identify the repository/app as LastNap and a personal fork of RikkaHub; use the existing exact cat launcher asset in the README header; label the upstream RikkaHub communities, website, Play Store app, sponsors, and star history explicitly as upstream; link LastNap's Releases page with a clear note that no LastNap release has been published yet. Do not invent a LastNap Discord/QQ community or claim upstream sponsors as LastNap sponsors. Keep sponsor affiliate URLs and all source/app behavior unchanged.
- Change only README presentation and download/community attribution text. Do not rewrite the full feature descriptions, poster art, documentation history, or license attribution in this pass.


## 21. Session handoff — 2026-11-05 (Stage 2 public README branding)

- Objective: align public repository landing pages with LastNap while preserving accurate attribution to the upstream RikkaHub project and avoiding false download/community/sponsor claims.
- Branch/source head: lastnap-clean-foundation, latest source commit before this plan update 82aa9acf2d4fd47d39d68591f8a9e4ae551c3d4e.
- Files changed: README.md (commit 700abf981d2c59462dab918dd04ceffc41da57e0), README_ZH_CN.md (commit 494d5bfa98715d8ab5b873fd46b60ad4e3ded395), and README_ZH_TW.md (commit 82aa9acf2d4fd47d39d68591f8a9e4ae551c3d4e). The title is LastNap; the header icon points directly to app/src/main/res/drawable-nodpi/appicon.png; the preview image remains unchanged with generic alt text; each README describes LastNap as a personal fork of RikkaHub.
- Download attribution: the GitHub Releases API returned no LastNap releases at review time, so each README says no LastNap release has been published yet and points to the LastNap Releases page for future verified builds. Original RikkaHub website and Google Play downloads remain available but are labelled explicitly as upstream/not LastNap.
- Community/sponsor/star attribution: upstream RikkaHub Discord/QQ community links remain intact and are labelled upstream. Sponsor rows and affiliate URLs were preserved but their section is titled as upstream RikkaHub sponsors / not LastNap sponsors. The star chart remains the upstream RikkaHub chart and is labelled accordingly. No new LastNap community or sponsorship relationship is invented.
- Final source-tree comparison before plan update: complete/non-truncated trees: LastNap 1,540 blobs vs frozen upstream 1,536; four LastNap-only paths (three artwork resources plus plan), zero missing upstream files, and 39 modified upstream paths. The 39 consist of 15 original identity/localization paths, nine OAuth paths, one app instrumentation test, four generated filename/Bonjour files, four notification icon source files, three embedded Web UI branding files, and three root README variants.
- Remaining work: the complete content-level scan of every tracked text blob is still not proven exhaustive. Targeted frozen-upstream searches and focused content review identified and corrected all user-facing product-brand omissions found, and the full tree/blob comparison verifies there are no unreviewed source-path modifications beyond the listed 39. Some old names are intentionally retained for provider identity, upstream attribution, internal WebView/localStorage identifiers, legacy QR/test fixtures, old rikkahub.local host compatibility, storage/Keystore/remote prefixes, document IDs, and internal service actions. Stage 2 remains open until the full tracked-text scan can be completed reliably.
- Verification status: all README files were fetched again and checked for their LastNap title, cat icon path, release page, and clearly labelled upstream links. No build/test/Gradle/JavaScript validation was run. The no-build/no-device status in Section 17 still applies.
- Next action: continue the complete legacy-string and text-blob audit; do not begin Stage 3 until Stage 2 is fully closed. Build, APK and Android 13 device verification remain Stage 8.


## 22. Additional exported-chat branding audit — 2026-11-05

- The launcher-resource search found that `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationExport.kt` bypasses the adaptive launcher icon and directly loads `R.mipmap.ic_launcher_foreground` for the logo embedded in exported conversation images. That direct resource remains the original upstream foreground artwork, so the exported image would still show the old bunny even though the app launcher uses the LastNap cat.
- The same export header visibly hard-codes `rikka-ai.com` next to the timestamp.
- **Planned minimal correction before editing:** use the existing `R.drawable.appicon` in the export image and change only the adjacent watermark from `rikka-ai.com` to `LastNap`. Preserve export layout, image dimensions, timestamp, chat data, and all other behavior. Keep the upstream website link in the About page as attribution.


## 23. Additional external-attribution and update-channel audit — 2026-11-05

- The RikkaHub upstream search for `rikka-ai.com` revealed one more visible export watermark in `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Mermaid.kt`, plus OpenRouter attribution headers in `ai/src/main/java/me/rerere/ai/util/Request.kt` and `mediagen/src/main/java/me/rerere/mediagen/provider/providers/openrouter/OpenRouterMediaGenerationProvider.kt`.
- **Planned minimal correction:** change the Mermaid PNG watermark from `rikka-ai.com` to `LastNap`; change only OpenRouter `X-Title` to `LastNap` and `HTTP-Referer` to the LastNap repository URL in the two request helpers. These are outbound client-attribution headers, not provider API endpoints.
- The About page's upstream RikkaHub website attribution and actual RikkaHub AI/search-provider endpoints remain unchanged. Do not replace API service endpoints merely because their domain contains `rikka-ai.com`.
- **Critical updater safety issue:** `UpdateChecker.kt` automatically contacts `https://updates.rikka-ai.com/` for non-Play builds after initialization and offers its returned APK downloads via `UpdateCard`. That would make a LastNap build query the upstream RikkaHub updater and could offer an APK with the wrong application ID. The check is active by default when the app is initialized and its disable-until timestamp has elapsed.
- LastNap's public GitHub Releases API currently returns an empty releases list. **Planned minimal safe migration:** make `UpdateChecker` query the public LastNap GitHub releases endpoint only; read the latest non-draft, non-prerelease release that contains APK assets; map its tag, publication time, changelog, and APK assets to the existing `UpdateInfo`/`UpdateDownload` UI model. When there is no published APK release yet, return a current-version/no-update state so no false update card appears. Preserve `UpdateCard`, DownloadManager behavior, existing update-disable timing, and the current UI; never fall back to the RikkaHub update server.
- The planned updater migration changes only the update source/JSON adaptation and visible User-Agent identity. It must not add tokens or require GitHub authentication because the LastNap repository/releases are public. No new LastNap release is to be fabricated or published as part of this audit.


## 24. Session handoff — 2026-11-05 (export identity and safe LastNap updates)

- Objective: remove remaining RikkaHub attribution from image exports and outbound OpenRouter headers, and prevent LastNap from offering upstream RikkaHub APKs through the inherited automatic updater.
- Branch/source head before this plan update: lastnap-clean-foundation, `b72dd93777f4450affbda28c3b80537663b1d2b2`.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ConversationExport.kt` (commit `c990ac5843dff6a5928b2d8cb14e75eb13b102ef`) now uses `R.drawable.appicon` instead of the upstream foreground icon in exported chat images and shows `LastNap` instead of `rikka-ai.com` beside the timestamp. Exact diff review found only these two intended lines.
- `app/src/main/java/me/rerere/rikkahub/ui/components/richtext/Mermaid.kt` (commit `3c340e8811747b83d5c66ac6a7a70b7eeb36e7a6`) now stamps generated Mermaid PNGs with LastNap instead of the upstream website; exact diff review found one intended line.
- `ai/src/main/java/me/rerere/ai/util/Request.kt` (commit `4f91e8b7f2d5393ad684c4daf80c9ca891ed8dd0`) and `mediagen/src/main/java/me/rerere/mediagen/provider/providers/openrouter/OpenRouterMediaGenerationProvider.kt` (commit `d8e0f482919fb224f583449d2678c04c3791c48a`) now send OpenRouter `X-Title: LastNap` and `HTTP-Referer: https://github.com/Cutie-Luna/LastNap`. Each file's only diff from frozen upstream is this two-header attribution pair.
- Upstream updater safety finding: `UpdateChecker` is automatically subscribed on initialized non-Play builds and would call `https://updates.rikka-ai.com/`, then expose its returned APK downloads in `UpdateCard`. That could offer the upstream app to a LastNap user with a different package ID. LastNap's public GitHub Releases API currently returns an empty list.
- `app/src/main/java/me/rerere/rikkahub/utils/UpdateChecker.kt` (commit `b72dd93777f4450affbda28c3b80537663b1d2b2`) now queries `https://api.github.com/repos/Cutie-Luna/LastNap/releases?per_page=20`, identifies itself as LastNap, deserializes published releases, skips drafts/prereleases and releases without APK assets, maps APK asset names/URLs/sizes into the existing `UpdateInfo` UI model, and returns a same-version/empty-download sentinel when no eligible APK exists. Existing UpdateCard, version comparison, DownloadManager, and disable-until preference behavior remain unchanged. The full 258-line updated source file was fetched and reviewed. No token/authentication is used.
- Post-edit verification: all five files fetched again; zero `RikkaHub` or `rikka-ai.com` literals remain in these files. The four non-updater file diffs were checked against the frozen upstream blob contents and contain only the planned replacements. The public GitHub Releases endpoint returned an empty array at verification time, matching the intended no-release path.
- Current known changed-path total from the last complete tree comparison was 44: the previous 39 plus ConversationExport, Mermaid, Request.kt, OpenRouterMediaGenerationProvider.kt, and UpdateChecker.kt. Re-run exact current tree comparison after this handoff update.
- No Gradle build, Kotlin compilation, unit/instrumentation test, lint, Web UI build, APK/signature inspection, install, or Android 13 device check has run. The migration is source-reviewed only; Stage 8 must verify the GitHub API JSON mapping and a real APK download when a LastNap release exists.
- Remaining limitation: all 1,453 text-like blobs are not yet individually opened. Complete recursive tree and per-path hash comparison are proven, all 44 changed source/resource paths plus 32 string resource files and relevant identity references were inspected, but do not claim full text-blob exhaustive scanning. The GitHub connector execution caps batch tool calls; the local archive/clone route is unavailable in the current environment.
- Next action: continue targeted old-brand and compatibility-reference review, then close Stage 2 only when a reliable full tracked-text scan is possible. Do not start Stage 3 before that. Stage 8 retains the build, tests, API/no-release behavior, and device verification gates.


## 25. Additional Gradle project-name identity check — 2026-11-05

- Reviewed root `settings.gradle.kts` and found `rootProject.name = "rikkahub"`. This name is presented by Gradle tooling and IDE project windows, although it does not affect the Android application ID, package namespace, build paths, or runtime behavior.
- **Planned minimal correction:** change only the root Gradle project display name to `LastNap`. Preserve every project include, plugin/dependency declaration, module name, Kotlin package, source path, and application namespace.


## 26. Session handoff — 2026-11-05 (Gradle project identity)

- Root `settings.gradle.kts` previously set `rootProject.name = "rikkahub"`, which appears in Gradle/IDE tooling. It now sets `rootProject.name = "LastNap"`; commit `2c91e23329f52b755ec936cd499ab103386b38cf`.
- Only the root display-name assignment changed. Project includes, module names, Gradle dependencies, build logic, Kotlin namespace/package, and source directories remain unchanged.
- Stage 2 checklist now includes this tooling-facing label. No Gradle configuration or build was executed after the change; build verification remains open.


## 27. Final source-tree and branch checkpoint — 2026-11-05

- Verified the working branch HEAD as `0ea59c72a616a6c0b5b1e48ee0cb38cbafe85778` before this checkpoint update.
- Re-fetched complete recursive Git trees for LastNap and the frozen upstream baseline. Neither response was truncated. LastNap has 1,540 blobs; upstream has 1,536 blobs.
- Exact path/blob comparison: 1,491 shared paths have blob hashes identical to upstream; 45 existing upstream paths have intentional LastNap changes; four LastNap-only paths exist (the two cat artwork PNGs, adaptive-icon black background XML, and this project plan); zero upstream paths are missing.
- The 45 changed upstream paths are fully enumerated in the preceding per-stage ledger rows. They cover app identity/version, app and OAuth localized branding, manifest/routes/shortcuts, notification and exported artwork, generated filenames, Bonjour display label, OpenRouter attribution, safe updater source, Web UI, root Gradle project name, package test expectation, and the three README variants. No unrelated feature/navigation/animation code paths differ from upstream.
- Re-verified branch protection/immutability by fetching branch tips: `master` remains `903e058b9361bff71d0b79f4ef2d8ac23d6597e6` and `feature/extensions-foundation` remains `942c1b49019dbbd55f02606f31b74cc258c14641`. Neither protected/historical branch was modified.
- Final focused checks: eight app locale files all say LastNap and contain zero `RikkaHub`; eight OAuth locale files and the OAuth callback source also contain zero `RikkaHub`; the four direct foreground notification builders plus shared notification default contain zero `ic_stat_rikkahub` references and use `noticon`; manifest, route constants and shortcuts have matching LastNap action IDs; adaptive icon references the cat appicon and monochrome noticon; exported chat and Mermaid images use LastNap identity; OpenRouter headers identify LastNap; the updater contacts only the LastNap releases endpoint.
- **Stage 2 remains open** for one uncompleted acceptance item: individually opening and scanning every one of the repository's 1,453 text-like blobs has not been completed. The full path/blob comparison proves 1,491 common files are byte-identical to frozen upstream, and all 45 differing source/resource files plus relevant identity surfaces have been read and reviewed. However, this is not a complete content-level scan of the unchanged text blobs; do not describe it as such or close Stage 2 until a reliable full checkout/scan becomes available.
- **Stage 8 remains open** for Gradle/Web UI build, unit/instrumentation tests, lint/format check, manifest and resource-table inspection, signed APK verification, installation, and Android 13 device validation. No phone action is needed yet; the next phone interaction will be required only for installation/runtime icon/notification checks after a clean signed APK is available.
- Do not begin Stage 3 or port Tool Builder/custom-tool work until Stage 2's source audit gate is formally closed. Preserve the current branch and all deliberate compatibility identifiers described above.


## 28. Additional runtime identity and test-parity audit — 2026-11-05

- A targeted source search found two runtime identity defaults not included in the earlier pass: `DataSourceModule.kt` emits `RikkaHub-Android/<version>` as the default User-Agent when the user leaves their custom User-Agent blank, and `SettingPreferencesNetworkPage.kt` shows the same value as its default placeholder/help text.
- The same search found `McpOAuthCoordinator.kt` sends `clientName = "RikkaHub"` to an MCP server when dynamic OAuth client registration is used and the user has not provided a custom MCP server name.
- The OpenRouter media implementation was already changed to LastNap attribution, but `mediagen/src/test/java/me/rerere/mediagen/provider/providers/openrouter/OpenRouterMediaGenerationProviderTest.kt` still asserts the old `X-Title: RikkaHub` value.
- **Planned minimal correction:** update the runtime and UI default User-Agent to `LastNap-Android/<version>`, change only the blank-name MCP dynamic-registration fallback from `RikkaHub` to `LastNap`, and update the OpenRouter media test expectation to `LastNap`. Existing saved custom User-Agent values and explicit MCP server names are preserved.
- Keep `MCP_OAUTH_CLIENT_METADATA_URL = https://rikkahub.github.io/oauth/client.json` unchanged for now: it is a hosted OAuth Client ID Metadata Document, not a cosmetic label, and the registered redirect URI may be coupled to that upstream client identity. Replacing it without hosting/registering a real LastNap client metadata document could break OAuth. Keep the upstream QQ/Discord community names, RikkaHub Search provider, SponsorAPI/service endpoints, internal `RikkaHubAPI`, and trace CLI identity unchanged where they identify upstream services rather than the LastNap app.


## 29. Lowercase localized product-name audit — 2026-11-05

- A case-insensitive scan of the eight app-module locale files found the ASR word-timestamp help string still refers to the app as `rikkahub` in every translation. This is a user-facing product-name mention, separate from the stable resource key `rikkahub_provider_description` that names the upstream RikkaHub hosted provider.
- **Planned minimal correction:** replace only the lowercase app-name token `rikkahub` with `LastNap` in `setting_asr_configure_step_timestamp_desc` across all eight app locale files. Preserve the rest of each translation and all resource keys.
- All eight OAuth locale files have zero case-insensitive `rikkahub` references. Search/UI locale resource case-insensitive verification will be included in the final focused check.


## 30. Stage 2 source-audit closure and Stage 3 entry — 2026-11-05

### Stage 2 source audit: closed with bounded-scan caveat

- **User direction:** the user approved proceeding to Stage 3 so the project can move forward after the identity work. This entry documents the scope decision instead of silently ignoring the earlier full-text-scan blocker.
- **Current source head before this plan update:** `51fbe308ba51b56f6d1ff65134d7d383ad2161e8` on `lastnap-clean-foundation`.
- **Complete tree comparison:** LastNap 1,540 blobs vs frozen upstream 1,536; 49 existing upstream paths differ intentionally; 4 LastNap-only paths (the two cat PNGs, adaptive icon black-background XML, and this plan); zero upstream paths missing. Both recursive tree responses were complete/non-truncated. The exact 49-path list was enumerated in the current session. `master` remains `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`; `feature/extensions-foundation` remains `942c1b49019dbbd55f02606f31b74cc258c14641`.
- **Identity review coverage:** all 49 differing paths reviewed against frozen upstream; all 32 tracked `strings.xml` files across app/OAuth/Search/UI read and checked case-insensitively; all 32 non-strings XML resources/manifests reviewed; app identity/version, source namespace, manifest authorities, shortcuts, RouteActivity, notification builders, adaptive/monochrome icons, export watermarks/icons, root Gradle name, embedded Web UI, README pages, update source, network User-Agent, MCP dynamic client name, and OpenRouter attribution were checked.
- **Locale result:** the eight app locales use `app_name = LastNap`; their only remaining case-insensitive `rikkahub` text is the stable `rikkahub_provider_description` resource key that names the upstream RikkaHub hosted provider. Their visible ASR-help references are now LastNap. All eight OAuth locale files and callback-generated page source have no RikkaHub brand text. All 16 Search/UI locale files have zero case-insensitive `rikkahub` text.
- **Intentional upstream/compatibility names retained:** source namespace/packages/classes and paths; `Theme.Rikkahub`; `RikkaHubAPI` and the RikkaHub Search provider; upstream QQ/Discord communities, About website attribution, sponsor/search APIs; MCP static Client ID Metadata Document URL pending a real LastNap registration; `rikkahub.local`/hostname and WebView origin; SharedPreferences/Keystore/remote backup/upload/document IDs; private internal service actions; provider/test fixture identifiers and the QR import fixture. These are not app-label mistakes and were not renamed.
- **Bounded-scan limitation:** the connector did not permit a practical whole-repository local checkout/archive and the 1,453 text-like blobs were not individually opened. This is not described as an exhaustive blob-content scan. However, exact tree/blob hashes prove that 1,491 shared paths are byte-identical to the frozen upstream snapshot; every differing path and every identity-bearing resource surface was inspected, and targeted legacy-identifier searches were run against the frozen baseline. No known user-facing LastNap identity mismatch remains. Record the remaining bulk scan as supplemental follow-up, not a blocker to Stage 3.
- **Stage 2 disposition:** source-level identity/artwork acceptance is closed on this documented basis. APK merged-manifest/resource-table, build, test, lint, signing, installation, and Android 13 launcher/notification behavior remain open in Stage 8; nothing in this closure claims those checks passed.

### Stage 3 — pre-edit objective and acceptance

- **Goal:** add the minimal persistence/data-model foundation for assistant-scoped custom tools, without editing existing screens or integrating runtime behavior yet.
- **First inspect (read-only):** current `Assistant` serialization model; `Settings` model and `SettingsStore`/DataStore serialization/migration entry points; `PreferencesStore` and settings migration tests; existing built-in tool definition/registration models and JavaScript sandbox; old `feature/extensions-foundation` custom-extension model and its tests as a reference only.
- **Design before edits:** define one user-level custom-tool record, a stable ID type, assistant-to-tool ID selection, default-empty migration for existing assistants, missing/deleted-ID behavior, name/description/schema/code limits, test-result invalidation semantics, and backup/export implications. Compare with existing serialization and migration conventions before choosing field names or schema.
- **Implementation boundary:** Stage 3 may add the persisted model, settings collection, assistant assignment IDs, datastore migrations/normalization, and focused pure/model tests. Do not add runtime tool registration/filtering or permission enforcement (Stage 4), Tool Builder UI (Stage 5), skills/entry points (Stage 6), or unrelated refactors.
- **Acceptance:** existing settings and assistants deserialize unchanged; new fields default safely (empty custom-tool collection and empty assignment IDs); round-trip serialization works; deletion/missing-ID normalization is defined and tested; oversized/invalid tool fields are rejected at the model/validation boundary; no tool becomes callable solely because it exists.
- **Risks:** Settings are user data and may be backed up/restored; migrations must preserve old serialized payload compatibility. IDs must be stable across backup/restore. New model fields must not collide with built-in `Tool` APIs. Avoid introducing a new database table unless existing repository patterns make Settings JSON storage unsuitable.
- **Next actions:** inspect the listed model/storage/runtime/test files on both the clean branch and historical feature branch; produce a file-level parity matrix and data-model proposal; only then update this plan with the chosen schema and begin Stage 3 edits.


## 31. Stage 3 data-model proposal — reviewed before source edits (2026-11-05)

### Chosen persisted shape

- Add `CustomExtension` as a user-level serializable record in `app/src/main/java/me/rerere/rikkahub/data/model/CustomExtension.kt`, based on the previously reviewed old-branch model to preserve the feature's existing conceptual shape: stable string UUID `id`, `name`, `description`, optional assistant instructions `skill`, JSON-object `inputSchema`, JavaScript `implementation`, `ExtensionPermission`, `enabled` (default false), and `testedImplementationHash` (default null). Keep test input/output as transient Builder UI state (Stage 5), not persisted tool data.
- Add `Settings.customExtensions: List<CustomExtension> = emptyList()` and a `custom_extensions` JSON-string DataStore key. Register the new field in `SETTINGS_FIELDS` exactly once, preserving the existing one-field/one-key storage invariant.
- Add `Assistant.selectedCustomExtensionIds: Set<String> = emptySet()` so each assistant explicitly opts in. Existing assistant JSON without the field deserializes to an empty set. A tool existing in `Settings.customExtensions` does not implicitly assign it to any assistant.
- Keep the old model defaults for safety: new tools start disabled, permission defaults to `ASK_EVERY_TIME`, and no successful test hash exists until a later test flow runs. Stage 3 does not make any tool callable.

### Validation contract

- Reuse the limits already exercised by the old feature branch: name 80 chars, description 2,000, skill text 16,000, input schema 16,000, implementation 64,000, and runtime input 64 KiB.
- Add a pure model-level validator that reports blank ID/name, over-limit fields, malformed JSON, and non-object root schemas. Blank description/skill/implementation remain allowed for an incomplete draft; a blank implementation can never count as tested/executable. Do not delete a user's draft merely because it is currently incomplete; Stage 4 runtime and Stage 5 save/enable actions must consult validation.
- The successful-test fingerprint is bound to both the exact input-schema text and implementation text. A schema edit must invalidate the prior result just like a code edit; this is implemented in the Stage 3 model and covered by focused tests.

### Normalization and deletion

- Extend `Settings.withoutInvalidReferences()` to drop duplicate/blank custom-tool IDs and remove assistant selections that do not refer to an existing custom tool. Keep valid selected IDs only.
- Add a pure `Settings.removeCustomExtension(id)` helper that deletes the definition and removes that ID from every assistant in the same Settings value. This provides an atomic cleanup primitive for the later UI without adding screens or runtime registration now.
- Do not auto-assign tools to default assistants. Missing or deleted definitions always leave assistants with no dangling selection.

### Migration and backup decision

- No DataStore version bump or V6 migration is needed: the new key is absent on old installations, and the existing field reader falls back to the `Settings` constructor default (`emptyList()`). The existing backup path serializes `Settings` to `settings.json` and decodes it with defaults; old backup JSON missing `customExtensions` and `selectedCustomExtensionIds` therefore remains compatible.
- Add regression tests for DataStore field mapping/round-trip/absent-key defaults, deserialization of pre-feature Settings and Assistant JSON, normalization of duplicate/deleted IDs, atomic deletion cleanup, and model validation limits/schema shape.

### Implementation boundary

- Stage 3 source files: new `CustomExtension.kt`; `Assistant.kt`; `Settings.kt`; `PreferencesStore.kt`; `SettingsFields.kt`; `SettingsNormalization.kt`; focused tests under `app/src/test/.../data/model` and `.../data/datastore`.
- Do not touch `ChatToolFactory.kt`, `JavascriptTool.kt`, runtime tool filtering, QuickJS execution, permission enforcement, Tool Builder screen, navigation, skills, or locales in Stage 3. Those belong to Stages 4–7.
- **Acceptance:** Settings/Assistant round-trip, old JSON defaults, default-empty assistant selection, cleanup/deletion, and validation tests are written and passed in the focused CI run recorded in Section 35. No existing Settings key or backup payload shape is renamed. No custom tool becomes callable.

## 32. Stage 3 test-fingerprint correction — 2026-11-05

- **Review finding before this edit:** the initial model stored `testedImplementationHash` and validated only the JavaScript implementation. However, changing `inputSchema` changes the input contract and can change how the tested implementation is invoked. An enabled tool could therefore retain a passing implementation hash after its schema changed.
- **Required correction:** bind the successful-test fingerprint to both the exact input-schema text and implementation text. Rename the not-yet-released field to `testedDefinitionHash`, and compute it from an unambiguous length-prefixed concatenation of schema and implementation before SHA-256 hashing.
- **Scope:** only the new custom-tool model and its focused unit tests. Do not change existing RikkaHub runtime code, tool registration, permissions, backup format, UI, or assistant behavior.
- **Acceptance:** tests prove the fingerprint changes when either schema or implementation changes; enabled definitions reject a missing/stale fingerprint; existing default-empty/legacy Settings and Assistant deserialization behavior remains intact. This is a pre-release model correction, so no released-data migration is required.

## 33. Release-workflow safety guard — 2026-11-05

- **Finding:** the only active build workflow, `.github/workflows/daily-build.yml`, scheduled a build twice daily and automatically published a public GitHub prerelease whenever a build succeeded. This contradicted the standing release rule in section 3: never publish a public release automatically.
- **Minimal safety correction:** remove the scheduled trigger; keep manual `workflow_dispatch`; add an explicit boolean `publish_prerelease` input defaulting to false; gate the public prerelease step on that input. Manual verification builds remain possible without publishing.
- **Scope:** workflow trigger and publish gate only. Do not change Gradle build tasks, signing setup, secrets, package identity, ABI, or APK configuration.
- **Acceptance:** no scheduled build or release can run; a manually dispatched build does not publish unless the user explicitly opts in through the workflow input.

## 34. Stage 3 persistence foundation — implementation checkpoint (2026-11-05)

### Branch and repository checkpoint

- Working branch: `lastnap-clean-foundation`. Source/workflow head verified by the successful Stage 3 CI run: `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c` (plan update follows as a separate commit).
- Protected branch tips rechecked: `master` remains `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`; historical `feature/extensions-foundation` remains `942c1b49019dbbd55f02606f31b74cc258c14641`. Neither was modified.
- Complete, non-truncated recursive tree comparison rechecked at commit `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c` against frozen fork baseline `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`: 1,544 LastNap blobs vs 1,536 baseline blobs; 8 LastNap-only paths; zero baseline paths missing; 58 shared paths differ; 1,478 shared blobs are byte-identical. The eight additions are the LastNap artwork/adaptive icon, custom-tool model, two focused test files, focused CI workflow, and this project plan. No baseline path was removed.
- A separate comparison against current upstream `rikkahub/rikkahub` master shows upstream has continued to move (including two newly added upstream-only assets); do not treat those newer upstream changes as omissions from the frozen fork baseline.

### Stage 3 source changes

- `app/src/main/java/me/rerere/rikkahub/data/model/CustomExtension.kt` — new serializable user-level tool definition, stable UUID-string ID, schema/code/description/skill limits, permission state, disabled-by-default draft state, structural validation, and successful-test fingerprint. The fingerprint binds both exact input-schema text and implementation text.
- `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt` — adds `selectedCustomExtensionIds: Set<String> = emptySet()`; old assistant JSON remains default-empty.
- `app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt` — adds `customExtensions: List<CustomExtension> = emptyList()`.
- `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` and `SettingsFields.kt` — register the `custom_extensions` JSON preference key and field mapping. No existing preference key is renamed.
- `SettingsNormalization.kt` — removes blank/duplicate tool IDs, cleans dangling assistant selections, and provides a pure deletion transformation that removes the definition and every assistant assignment together.
- `.github/workflows/stage3-persistence-tests.yml` — new branch-scoped, non-publishing focused test workflow; JDK 21 / Node 22 / pnpm 11, CI-only dummy Google Services configuration, `contents: read` permission, no signing secrets, no release task, and no artifact publication.
- Tests added/extended:
  - `CustomExtensionTest.kt`: disabled safe defaults, schema shape/malformed JSON, field limits, enable/test gate, disabled-permission rejection, and schema/code fingerprint invalidation.
  - `SettingsNormalizationTest.kt`: no implicit assignment, duplicate/dangling-reference cleanup, and atomic deletion cleanup.
  - `SettingsFieldsTest.kt`: one-to-one field mapping, DataStore round trip, absent-key defaults, legacy Assistant/Settings JSON defaults, and stored-key compatibility.
  - `SettingsStoreTest.kt`: custom-tool records and assistant selections survive a DataStore close/reopen.
- No V6 migration or database table is needed: the new DataStore key is absent on old installs and resolves through the empty Settings default; old backup `settings.json` is decoded through the same serializable Settings model and missing fields default safely. `BackupManager.kt` and `SettingsJsonMigrator.kt` were inspected and intentionally left unchanged.
- No runtime registration, filtering, QuickJS execution, permission prompt, Builder UI, navigation, skills, or unrelated RikkaHub screens were changed. `ChatToolFactory.kt` remains unchanged, so persisted definitions do not become callable merely by existing.

### Runtime and historical-feature inspection for the next stage

- Current `ChatToolFactory` composes built-in, selected local, workspace, skill, and MCP tools; it has no custom-extension registration.
- Current JavaScript execution is QuickJS/ES2020 with a fresh bounded runtime, 10-second execution timeout, 64 MiB memory limit, bounded stack/log/result output, and no DOM/Node/Android bindings. The existing sandbox helper is reusable, but custom tool code must not add new host capabilities.
- The internal `Tool` contract exposes a name, description, `InputSchema.Obj` parameters, approval predicate, and suspend execution callback. MCP's schema adapter provides a precedent for converting richer JSON Schema while inlining local references.
- Historical `CustomExtensionTools.kt` is reference-only, not ported. It filtered by assistant selection, enabled state, permission, size, schema validity, and test hash; it wrapped invocation input and reused QuickJS. Its old fingerprint covered implementation only, which is why the new persisted model now requires a schema-plus-implementation fingerprint.
- Before Stage 4 implementation, explicitly review approval-resumption semantics for `ASK_EVERY_TIME` versus `THIS_SESSION`, tool-name collision/normalization, schema adaptation limits, input/output limits, and the current assistant's selected IDs. Do not assume the old branch's permission predicate is sufficient.

### Release-workflow safety

- `.github/workflows/daily-build.yml` previously scheduled builds twice daily and automatically published a public prerelease after a successful build. This contradicted the project's explicit no-automatic-publication rule.
- The schedule and obsolete scheduled-build check job are removed. Manual `workflow_dispatch` remains, with `publish_prerelease` defaulting to false; the release step runs only when that input is explicitly enabled. Gradle task, signing configuration, and secret references are unchanged.

### Verification and remaining gate

- Source files and focused test cases were fetched and reviewed after the final edits. The stale implementation-only test fixture was corrected to use the schema-plus-implementation fingerprint.
- **Focused CI verification passed:** GitHub Actions run [42028485101](https://github.com/Cutie-Luna/LastNap/actions/runs/42028485101), workflow `Stage 3 Persistence Tests`, branch `lastnap-clean-foundation`, head `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c`; job `persistence-tests` completed with `success`. The `testGithubDebugUnitTest` Gradle task passed with filters for `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, and `SettingsStoreTest`. The task compiled the GithubDebug app and unit-test sources and executed the focused tests; Gradle reported `BUILD SUCCESSFUL` (228 actionable tasks). No test failure occurred. Existing deprecation/opt-in compiler warnings were non-fatal and were not expanded into unrelated cleanup.
- The workflow used a temporary CI-only Google Services file and read-only repository token permissions. No signing key/secrets, signed APK task, release task, publication, or artifact upload was involved.
- The focused `testGithubDebugUnitTest` task and its required source/test compilation passed. No separate lint task, full assemble task, signed APK build, signature check, install, or phone verification was run. No phone action is needed for Stage 3.
- **Stage 3 disposition:** complete. The acceptance tests and relevant compile path passed in CI. Keep the focused workflow as a reusable regression check.
- **Next action / Stage 4 entry:** review the live assistant tool-registration path and the historical implementation only as reference; explicitly analyze `ASK_EVERY_TIME` versus `THIS_SESSION` approval resumption, tool-name collisions/normalization, JSON-schema adaptation limits, input/output bounds, and current-assistant selection filtering. Write the Stage 4 design and acceptance criteria into this plan before editing runtime files.

## 35. Focused custom-tool test-runner and successful execution — 2026-11-05

- Added `.github/workflows/stage3-persistence-tests.yml` on `lastnap-clean-foundation`. It runs on pushes to that branch (and supports manual dispatch), uses JDK 21 / Node 22 / pnpm 11, creates a CI-only dummy `app/google-services.json`, and runs the four Stage 3 persistence/model test classes plus the Stage 4 CustomExtensionToolsTest through `:app:testGithubDebugUnitTest`. The workflow display name is now “Custom Tool Model and Runtime Tests”.
- Safety review of the workflow: `permissions: contents: read`; no signing secrets or keystore access; no release/assemble task; no artifact upload or publication. The dummy Google Services file exists only in the ephemeral runner workspace.
- First run: [GitHub Actions run 42028485101](https://github.com/Cutie-Luna/LastNap/actions/runs/42028485101), event `push`, branch `lastnap-clean-foundation`, commit `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c`, run attempt 1. Overall run and sole `persistence-tests` job both completed successfully.
- The log confirms all setup steps completed, Gradle compiled the GithubDebug app and unit-test sources, then `:app:testGithubDebugUnitTest` completed with `BUILD SUCCESSFUL` in 4m 43s (228 actionable tasks). The four filters were `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, and `SettingsStoreTest`. The log contains non-fatal existing deprecation/opt-in warnings; no test or compile failure.
- **Disposition:** Stage 3 acceptance is met and Stage 3 is complete. The focused workflow remains as a reusable non-publishing regression check. This result is not a lint result, full APK build, signing verification, install, or device test.
- The workflow was extended for Stage 4 without changing its safety boundary: read-only repository permissions, CI-only Google Services configuration, no signing secrets, no release task, no publication, and no artifact upload.


## 36. Stage 4 runtime and permission design — reviewed before source edits (2026-11-05)

### Current runtime path inspected

- ChatToolFactory.createTools assembles built-in, local, conversation, workspace, skill, and MCP tools for the assistant. It currently does not register custom extensions. ChatRequestFactory.createContext builds that tool list once per turn, and createAgent passes the same list to AgentRequest; AgentLoop uses it both to publish schemas and to resolve tool calls.
- AgentLoop.requestApprovals checks Tool.needsApproval only for new calls whose state is Auto. An approved pending call resumes the same assistant message; remaining pending calls are checked before execution. This means the approval callback must be re-created consistently when ChatService.runTurn rebuilds the request after a user decision.
- ChatService.handleToolApproval is the correct place to record a session grant after an explicit approval and before runTurn resumes. Denials and AskUser answers must not create grants.
- JavascriptTool.kt already provides a fresh QuickJS runtime with a 10-second timeout, 64 MiB memory limit, 256 KiB stack limit, bounded logs and a 1 MiB result limit. It exposes no DOM, Node, Android, filesystem or network bindings. Custom tools must reuse this helper and must not add host capabilities.
- InputSchema.Obj carries a properties map and required list. The historical custom-tool implementation wrapped the user schema under a required input property; retain this shape so the complete user-authored object schema (including root-level JSON Schema keywords) remains nested and is not silently discarded by the narrower internal schema type.
- The historical UI string explicitly described THIS_SESSION as “Allow this session (currently asks each time)”. The old runtime treated it exactly like ASK_EVERY_TIME; that is unfinished behavior, not a working implementation to copy.

### Chosen Stage 4 behavior

- Register only extensions whose IDs are selected by the current assistant, which are enabled, have no validationErrors(), have a current schema-plus-implementation test fingerprint, and are not DISABLED. A saved definition alone never registers a tool.
- Use a deterministic provider-safe tool name derived from both the extension ID and its current definition fingerprint. The name therefore changes when the input schema or implementation changes. A pending approval for an older definition then cannot resolve to newly edited code when the request resumes; it safely becomes an unknown-tool result instead of executing a different definition than the one the user saw.
- Add custom tools after the existing tools. If a generated custom name collides with any built-in/local/workspace/skill/MCP name or another custom name, skip the custom tool and log the collision; never shadow an existing tool or rely on findTool's first-match behavior.
- Expose the complete input schema as the nested input property and require that property. At invocation, require a JSON object and enforce the existing 64 KiB input limit using UTF-8 byte size before embedding the serialized JSON as the JavaScript input constant.
- Reuse evaluateJavascript in the same local package. Do not add any JavaScript bindings, network/filesystem access, Android APIs, or alternate execution engine. Preserve the existing bounded runtime and return its structured result as tool output.
- Permission semantics: ALWAYS_ALLOW does not prompt; ASK_EVERY_TIME prompts for every new invocation; THIS_SESSION prompts until the user approves this extension for the current conversation in the current app process, then suppresses further prompts for the same assistant + extension ID + definition fingerprint; DISABLED excludes the tool entirely. A denied call does not grant session approval. Grants are in-memory only, survive navigation/session-object eviction, and reset on app-process restart or conversation deletion. They are not written to Settings or backups.
- Store grants in ChatService keyed by conversation ID and pass a snapshot into ChatRequestFactory/ChatToolFactory when building a normal turn. Include assistant ID, extension ID, and definition fingerprint in each grant. Clear grants when a conversation is deleted and when ChatService.cleanup() runs. An assistant change cannot reuse another assistant's grant.
- On approval, inspect the pending tool call's exact generated name and match it against the currently selected, enabled, valid THIS_SESSION extension. Only an explicit approval of that current definition adds a grant. If its input schema or implementation changed while approval was pending, the versioned tool name changes and the old call cannot resolve to the new definition; if the extension was removed, disabled, or deselected, it is not registered. Display-name, description, or skill-only edits do not change the execution fingerprint by themselves.

### Intended source/test scope

- New app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionTools.kt for registration, safe name generation, schema wrapping, permission predicates, input validation, and QuickJS execution.
- Minimal integration in ChatToolFactory.kt, ChatRequestFactory.kt, and ChatService.kt to pass assistant-scoped tools and per-conversation session grants. Do not change AgentLoop, Tool, built-in tool implementations, navigation, or unrelated RikkaHub screens unless a concrete test proves an unavoidable requirement.
- New focused CustomExtensionToolsTest.kt covering assistant selection, disabled/untested/invalid definitions, all four permission modes, session grants, versioned names, name collisions, schema preservation, JSON-object/UTF-8 input limits, and execution through the existing sandbox. Add the smallest practical integration tests for grant recording/resumption if existing service test seams permit it.
- Acceptance: only the current assistant's selected, enabled, valid, tested custom tools are offered; stale definitions cannot execute after edits; permissions behave exactly as defined above; the current sandbox remains the only execution environment; existing built-in tool behavior is unchanged.

### Stage 4 risks and verification limits

- THIS_SESSION is explicitly defined here as a conversation-scoped, in-memory grant lasting for the app process, rather than a persisted permission or a short-lived ConversationSession object (which is evicted after an idle timeout). This avoids surprising re-prompts when navigating away and back while still resetting grants after process restart.
- A model-provided schema is a contract for tool-call generation, not a security boundary. Runtime must still validate the actual JSON object and byte limit. Do not claim full JSON Schema validation unless a complete validator is implemented and tested.
- Focused unit tests can validate the pure registration/permission/input logic; they do not replace provider-specific schema compatibility or device verification. Keep Stage 7 integration/regression and Stage 8 build/device gates open.
- Stage 4 implementation and verification are recorded in Section 37. The design above remains the acceptance reference; Stage 7 still owns end-to-end integration/regression verification.


## 37. Stage 4 runtime implementation and focused verification — 2026-11-05

### Source changes on lastnap-clean-foundation

- Added app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionTools.kt. It registers only the current assistant's selected, enabled, structurally valid, currently tested definitions; preserves the complete object schema under the required input property; uses provider-safe names bound to the extension ID and schema/code fingerprint; rejects duplicate IDs and name collisions; escapes custom skill text before adding it to the tool system prompt; and runs code only through the existing evaluateJavascript QuickJS helper.
- Runtime input handling requires a JSON object and applies the 64 KiB limit to the complete argument payload using UTF-8 byte size. It serializes the input as a JSON string and reconstructs it with JSON.parse, so special property names such as __proto__ remain ordinary JSON data. The existing QuickJS time, memory, stack, log, and result limits remain unchanged; no host APIs or capabilities were added.
- Updated ChatToolFactory.kt to append custom tools after the existing tool set and skip/log collisions rather than shadow existing tools. Updated ChatRequestFactory.kt to pass per-conversation grants into tool construction. Updated ChatService.kt to record a THIS_SESSION grant only after the matching pending call is explicitly approved and its approval-state update succeeds.
- Historical Stage 4 permission behavior was superseded by Section 40: custom tools now store one `needsApproval` boolean and rely exclusively on the existing generic `Tool.needsApproval` / ChatService approval path. The custom-only session-grant map and four-mode permission policy were removed.
- Versioned tool names prevent a pending approval for an older schema/code definition from resolving to newly edited code. If schema or implementation changes while approval is pending, the old tool name no longer resolves; if the tool is disabled, removed, or deselected, it is not registered.
- No changes were made to AgentLoop, the core Tool model, built-in tool implementations, navigation, existing chat screens, or unrelated RikkaHub behavior.

### Focused tests and CI result

- Added app/src/test/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionToolsTest.kt with 11 focused tests covering assistant selection and validity, schema preservation, all permission modes, exact session-grant matching, versioned names, collision handling, required-schema structure, QuickJS execution, safe handling of __proto__, JSON-object and UTF-8 argument limits, and duplicate IDs.
- Final verification: [GitHub Actions run 42034011902](https://github.com/Cutie-Luna/LastNap/actions/runs/42034011902), branch lastnap-clean-foundation, commit 2f47a4706fd6fc1cd6b7603f376957e5362e451d. The overall run and persistence-tests job completed with success. The workflow executed :app:testGithubDebugUnitTest with filters for CustomExtensionTest, SettingsNormalizationTest, SettingsFieldsTest, SettingsStoreTest, and CustomExtensionToolsTest.
- The log reports BUILD SUCCESSFUL in 3m 44s (228 actionable tasks). The app and unit-test sources compiled and all five selected test classes passed. The web-ui bundling emitted non-fatal sourcemap-location warnings; they did not fail the build and were not expanded into unrelated cleanup.
- The CI-only Google Services file was generated in the ephemeral runner workspace. No signing key/secrets, signed APK task, release task, publishing, or artifact upload was used.

### Full-tree audit at the verified source head

- At source head 2f47a4706fd6fc1cd6b7603f376957e5362e451d, the complete recursive tree was non-truncated: 1,546 LastNap blobs versus 1,536 blobs in frozen fork baseline 903e058b9361bff71d0b79f4ef2d8ac23d6597e6; 10 LastNap-only paths, zero baseline paths missing, and 61 shared paths with different blob hashes. The other 1,475 shared blobs are byte-identical to the frozen baseline.
- Compared with live upstream RikkaHub master f958af6a1a4a16dd8c1ac4138662a838b590e6c5, the non-truncated trees contain 1,546 LastNap blobs and 1,538 upstream blobs: 10 LastNap-only paths, 2 upstream-only paths, and 62 shared paths with different hashes. The two upstream-only paths are app/src/main/assets/icons/sta1n.png and docs/sponsors/sta1n.png; they are unrelated sponsor assets and were not copied as part of this custom-tool stage.
- The two new Stage 4 source paths are CustomExtensionTools.kt and CustomExtensionToolsTest.kt. The three existing runtime integration paths are ChatToolFactory.kt, ChatRequestFactory.kt, and ChatService.kt. The focused workflow was extended; no baseline path was removed.

### Stage 4 disposition and remaining gates

- **Stage 4 source/runtime implementation: complete.** The focused CI run compiled the integration and passed the pure registration, permission, schema, input-bound, and QuickJS execution tests.
- No separate lint task, full assemble task, signed APK build, signature check, install, or phone verification was run. This is intentional: those remain Stage 8 gates. No phone action is needed from the user now.
- The ChatService approval/resumption path was compiled and reviewed, while the focused tests exercise its exact grant-resolution policy rather than a full live conversation. Provider-specific schema compatibility and full chat/UI integration remain Stage 7 verification work.
- **Next stage:** Stage 5 — implement the Simple / Advanced Tool Builder interface, following the documented original-RikkaHub-preservation boundary. Do not begin Stage 6 skills/entry points until Stage 5 is reviewed and accepted.


## 38. Stage 5 Builder UI design and parity checklist — reviewed before UI edits (2026-11-05)

### Existing implementation inspected

- Historical reference: app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderPage.kt on feature/extensions-foundation (478 lines). It contains a global draft list and one AlertDialog editor. The editor is the cramped modal being replaced; the surrounding RikkaHub navigation and extension pages are not targets for redesign.
- The old Builder's data flow uses SettingsStore.settingsFlow and SettingsStore.update, creates or edits CustomExtension records, runs the existing evaluateJavascript QuickJS helper, and removes an edited/deleted tool's assistant references when deleting it. These are reusable behaviors, not permission to port the old page wholesale.
- Two historical test-flow defects must not return: its successful-test hash covered only implementation text, and it interpolated parsed JSON into a JavaScript object literal. The current model uses CustomExtension.testFingerprint() over exact schema + implementation, and the Stage 4 runtime reconstructs input with JSON.parse. The Builder must follow those current contracts and must use testedDefinitionHash, not the removed historical field name.
- The clean branch intentionally has no ExtensionBuilderPage, Screen.ExtensionBuilder route, or assistant custom-tools tab. The historical assistant-specific tab/selector belongs to Stage 6; do not restore or alter AssistantExtensionsPage in Stage 5.
- Current ExtensionsPage is a small, original-style global hub with Quick Messages, Prompts, Agent Skills and Workspace cards. Add only a focused Custom Tools entry there to reach the new dedicated Builder page; do not add assistant-specific selection controls or modify other extension cards.

### Builder parity checklist (all existing popup behavior retained)

- Draft collection: empty state, draft name/description display, create-new action, open/edit an existing draft.
- Definition fields: name; description/purpose; assistant instructions (skill); JSON-object input schema; JavaScript implementation.
- Test fields and state: sample JSON-object input; running/loading state; sandbox execution; successful result display; error/failed result display; current-test indicator; edited/stale-test indicator.
- Enablement: explicit checkbox only after the current schema + implementation have passed a test and the complete model validation succeeds. Editing either schema or implementation clears the test marker, clears the prior result and unchecks enabled. Other descriptive edits do not invalidate the execution test.
- Permissions: preserve ALWAYS_ALLOW, ASK_EVERY_TIME, THIS_SESSION and DISABLED, with clear explanations that match the Stage 4 runtime behavior.
- Actions: save as new draft; update existing draft; delete existing draft with confirmation and clean all assistant references; leave/cancel without saving; run the test.
- States to cover: empty list; new incomplete draft; malformed/non-object/oversized schema; invalid/oversized test input; empty or oversized implementation; running test; failed test; successful current test; stale test after schema/code edits; disabled draft; enabled valid definition; all permission choices.
- Preview/review is informational only: summarize name, description, input schema, implementation, permission, enablement and current test status. It must not execute JavaScript, mark a test successful, save, or enable a tool.

### Stage 5 UI design decisions

- Replace the AlertDialog with a dedicated page-like editor hosted by Screen.ExtensionBuilder. Use the existing LargeFlexibleTopAppBar, BackButton, CustomColors and original NavDisplay transition defaults. Do not modify global navigation animation specifications.
- Keep the draft list as the Builder landing state and switch to a full-screen editor state for create/edit. Use a single-column LazyColumn with grouped sections and comfortable spacing, optimized for phones.
- Provide a clearly visible Simple / Advanced switch. Both modes edit exactly the same draft fields. Simple mode emphasizes plain-language purpose/instructions and keeps technical schema/code/sample fields under a clearly labeled expandable “Build details” group; Advanced mode shows all fields in readable groups. No AI-generation action or tool-creator skill is introduced here; those belong to Stage 6. Simple mode explains how the user can use natural language and paste generated schema/code from an assistant without pretending LastNap has integrated a builder assistant yet.
- Add a distinct Review state before saving. It is a read-only summary; return to editing to change anything. The review footer offers Back to edit and Save draft. It never runs tests or turns on enablement.
- Keep a persistent, unobtrusive bottom action area on the editor for Back, Run test and Review. Keep Delete as a separate confirmed action inside the editor for existing drafts, avoiding a crowded footer.
- Draft persistence stays in SettingsStore and the persisted CustomExtension format. Test input/output remain transient UI state; do not add persistence or a database. Saving an incomplete draft is allowed for recovery, but invalid definitions remain disabled and cannot become runtime tools.
- Show the actual THIS_SESSION semantics: approval is remembered for this conversation and definition during the current app process. Do not reuse the old inaccurate “currently asks each time” label.
- Add the Builder route and a Custom Tools card to the global Extensions hub only. Leave assistant custom-tool assignment UI for Stage 6 and all unrelated pages/screens unchanged.

### Stage 5 intended source and test scope

- New app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderPage.kt: list, full-page editor, Simple/Advanced presentation, Review state, validation/test/enable/save/delete flow.
- Minimal update to app/src/main/java/me/rerere/rikkahub/RouteActivity.kt: import and register Screen.ExtensionBuilder using the existing default transition behavior; add the Screen key.
- Minimal update to app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionsPage.kt: one Custom Tools entry card.
- Add only required English base strings in app/src/main/res/values/strings.xml. Translations are reserved for Stage 7.
- Add focused state/logic tests if pure Builder validation/test-gate logic can be extracted without adding a new ViewModel or broad UI test framework. CI must at least compile the page and rerun the focused model/runtime tests.
- Source acceptance: no add/edit popup editor remains; the Builder has a full-page editor; both modes and the parity checklist are retained in source; Review is informational; test-before-enable and schema+code fingerprint rules are enforced; permissions match Stage 4; delete cleans assistant references; no unrelated RikkaHub UI or behavior is intentionally changed. Phone readability/visual acceptance is not claimed until a real installed build is reviewed.


## 39. Stage 5 Builder UI implementation and focused verification — 2026-11-05

### Files changed on lastnap-clean-foundation

- Added app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderPage.kt. The old add/edit AlertDialog editor is replaced by a dedicated full-page editor. The page retains a global draft list and its empty state, create/edit, name, description, assistant instructions, input JSON Schema, JavaScript, test input, sandbox test, test result/status, enable checkbox, permission controls, save/update, delete confirmation, and Back/cancel behavior.
- Simple mode keeps the purpose/instructions fields prominent and places schema, implementation, and sample input under an expandable Build details group. Advanced mode displays those technical sections directly. Both modes edit the same CustomExtension shape and share the same validation/test/enable/save logic.
- Added a read-only Review state with a separate bottom action area. It summarizes the fields, permission, test status, and enablement disposition; it does not run code, mark tests successful, or enable the tool. The editor uses a single-column scrollable layout and a persistent bottom action area with Back, Run test, and Review.
- The page runs sample code through the existing evaluateJavascript QuickJS helper, with test input constructed by buildCustomExtensionTestCode using JSON.parse and the same UTF-8 argument-size limit as the runtime. A success marker is stored only for the exact schema+implementation testFingerprint; editing either clears the marker/result and disables the tool. Descriptive text changes do not invalidate the test.
- Builder enablement consults the complete CustomExtension.validationErrors() model gate. During Stage 5 review, the model's JSON Schema shape validation was aligned with the Stage 4 runtime: a present properties member must be a JSON object, and a present required member must be an array of strings. Added model and Builder state assertions so malformed structures cannot appear ready in the Builder and then be silently rejected by runtime registration.
- Saving an incomplete draft remains allowed, but it is saved disabled. Enabling requires a current successful schema+code test and no validation errors. The four permission modes described in the initial Stage 5 implementation were superseded by Section 40. The Builder now exposes only the standard “Need approval” switch.
- Existing-tool deletion uses the existing Settings.removeCustomExtension transformation, removing the saved record and all assistant assignments together.
- Added app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderState.kt and app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderStateTest.kt. The five focused tests cover fingerprint invalidation and stability, enablement validation, safe JSON.parse input construction and __proto__ preservation, object-input requirements, UTF-8 size limits, and valid test-code construction.
- Updated app/src/main/java/me/rerere/rikkahub/RouteActivity.kt with Screen.ExtensionBuilder and its entry, using the existing default navigation transition behavior. No global transition specifications were changed.
- Updated app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionsPage.kt with a single Custom Tools card linking to the Builder. Existing Quick Messages, Prompts, Skills, and Workspace entries remain intact. Assistant-specific tool selection UI remains reserved for Stage 6.
- Updated app/src/main/res/values/strings.xml with the Builder's English UI labels and descriptions, and two Custom Tools hub labels. Other locales remain reserved for Stage 7.
- The first CI attempt, run 42038293735 (https://github.com/Cutie-Luna/LastNap/actions/runs/42038293735), failed during Android resource merge because the Review introduction contained an unescaped possessive apostrophe. This was corrected to “change whether the tool is enabled”; it was a resource-text issue, not a Kotlin or runtime failure.

### Final focused CI verification

- Final source head: 6c34c9559d84927353e9b9b2a0d961e7e79ca9fa.
- Successful GitHub Actions run: 42038947611 (https://github.com/Cutie-Luna/LastNap/actions/runs/42038947611). Overall run and sole persistence-tests job both completed with success.
- The workflow ran :app:testGithubDebugUnitTest with six focused test-class filters: CustomExtensionTest, SettingsNormalizationTest, SettingsFieldsTest, SettingsStoreTest, CustomExtensionToolsTest, and ExtensionBuilderStateTest.
- Log confirms :app:compileGithubDebugKotlin and :app:testGithubDebugUnitTest completed; Gradle reported BUILD SUCCESSFUL in 2m 20s (228 actionable tasks). The page and app Kotlin sources compiled, and all six selected test classes passed.
- Web UI build emitted existing non-fatal sourcemap-location warnings; they did not fail the run. CI used the ephemeral dummy Google Services configuration and read-only workflow permissions; no signing secrets, signed APK, release task, publication, or artifact upload was involved.

### Full-tree audit at final Stage 5 source head

- All three recursive trees were non-truncated.
- Compared with frozen LastNap fork baseline 903e058b9361bff71d0b79f4ef2d8ac23d6597e6: 1,549 LastNap blobs versus 1,536 baseline blobs; 13 LastNap-only paths; zero baseline paths removed; 62 shared paths with different blob hashes; 1,474 shared blobs byte-identical.
- Compared with live upstream RikkaHub master f958af6a1a4a16dd8c1ac4138662a838b590e6c5: 1,549 LastNap blobs versus 1,538 upstream blobs; 13 LastNap-only paths; 2 upstream-only paths; 63 shared paths with different blob hashes; 1,473 shared blobs byte-identical. The two upstream-only paths remain the unrelated sponsor assets app/src/main/assets/icons/sta1n.png and docs/sponsors/sta1n.png.
- Protected branch tips were rechecked: LastNap master remains 903e058b9361bff71d0b79f4ef2d8ac23d6597e6; feature/extensions-foundation remains 942c1b49019dbbd55f02606f31b74cc258c14641. Neither branch was modified.

### Stage 5 disposition and handoff

- **Builder source implementation and focused CI compile/test verification: complete.**
- **Real-phone visual acceptance: open.** CI compiles the page but does not show how the full-page editor feels on the target Android 13 phone. Do not claim phone readability, runtime navigation feel, or visual polish has been verified. The user's review should happen when a build is available through the Stage 8 verification path; no phone action is needed before then.
- No separate lint task, full assemble task, signed APK build, signature check, install, screenshot, or device test was run.
- Stage 7 remains responsible for translated strings and integration/regression polish. Stage 8 remains responsible for full build, package/resource table checks, existing-key signing verification, installation, and Android 13 device checks.
- **Stage 6 is intentionally not started.** It adds the Tool Creator skill and assistant-specific custom-tool assignment/entry points. Start only after the user has reviewed and accepted the Stage 5 Builder presentation.


## 40. User-approved Stage 4/5 alignment and Stage 6 execution plan — 2026-11-05 (pre-edit)

The user approved continuing with corrections to Stages 4 and 5, then proceeding to Stage 6 once the corrections are confirmed by source review and focused CI. This approval supersedes the earlier Stage 5 note that AI assistance was not integrated and the four-mode permission presentation.

### Agreed product behavior

- The AI helps prepare custom tools in BOTH Simple and Advanced Builder modes. Simple mode is plain-language-first; Advanced mode exposes technical fields, but the AI can still draft and revise the schema, instructions, JavaScript implementation, and test input for either mode. The user places/reviews the generated content in the Builder, runs the real sandbox test, and explicitly controls saving, enabling, and assistant assignment. No AI path may mark a test as passed or enable a tool.
- Replace the four-way ExtensionPermission policy with one persisted boolean, `needsApproval`, shown as a standard app-style on/off switch labelled “Need approval”.
- A new tool defaults to approval required. When enabled, the custom Tool uses the existing generic `Tool.needsApproval` contract. The existing chat tool-call approval UI and ChatService `handleToolApproval` flow remain the only approval mechanism. No custom approval card, session grant, or second prompt mechanism is to be added.
- When “Need approval” is off, the existing generic approval system is not invoked for that custom tool. When on, every invocation uses the same existing approval UI/decision path as other approval-requiring tools.
- Remove the custom-only THIS_SESSION grant system and four permission branches. Keep assistant selection, enabled/current-test validation, schema checks, QuickJS sandbox limits, and versioned tool names unchanged.
- Legacy serialized `permission` values are ignored by the new model field and `needsApproval` defaults to true, a safe migration that may add an approval prompt to previously always-allowed tools rather than silently preserving an unsafe no-prompt behavior.

### Stage 4/5 correction scope and acceptance

- Update CustomExtension model/validation, runtime registration, ChatService/ChatRequestFactory grant plumbing, Builder switch, English strings, and focused model/runtime/Builder tests.
- Confirm that no custom approval mechanism remains and the generic chat approval flow is still reached via `Tool.needsApproval`.
- Update Simple and Advanced Builder guidance to explicitly say the AI can prepare/refine every field in either mode; user pastes/reviews the result and runs the actual test.
- Keep the existing roomy page, review state, draft persistence, stale-test invalidation, and all unrelated RikkaHub UI unchanged.
- Focused CI must compile the app and pass the relevant persistence, model, runtime, and Builder tests before Stage 6 begins. Device/phone visual review remains a Stage 8 check; source/CI confirmation is not a claim of device verification.

### Stage 6 — Tool Creator skill and assistant entry points (approved to begin after the above gate)

- Add the existing reviewed historical `tool-creator/SKILL.md` as a built-in skill alongside `skill-creator`; review and adapt its instructions to the corrected one-toggle/existing-approval design and AI-assisted Simple/Advanced workflow.
- Keep skill opt-in and assistant-scoped through the existing `Assistant.enabledSkills` mechanism. Do not inject instructions globally.
- Restore only the assistant-specific Custom Tools selector/content and the Custom Tools tab in the assistant Extensions page, reusing the existing `Assistant.selectedCustomExtensionIds` field and cleanup behavior already implemented in Stages 3/4.
- Add a direct Tool Builder entry point within that assistant-specific Custom Tools area. Keep the global Extensions hub entry and all existing assistant extension tabs intact.
- Verify an assistant without the skill receives no Tool Creator instructions, and assistants without selected custom tool IDs receive no custom tools.
- Add focused tests for built-in skill discovery/merge and assistant-specific selection behavior where supported by the existing architecture.
- Do not port unrelated historical changes, redesign chat approval UI, change global navigation transitions, or modify built-in RikkaHub tools.

### Pre-edit audit for this session

- Working branch: `lastnap-clean-foundation`, starting HEAD `010762c4ed24b302ea6d2fb3425b5471bb43cb5b`.
- Protected refs rechecked: LastNap `master` remains `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`; historical `feature/extensions-foundation` remains `942c1b49019dbbd55f02606f31b74cc258c14641`.
- Upstream RikkaHub master rechecked at `f958af6a1a4a16dd8c1ac4138662a838b590e6c5`.
- Relevant current sources reviewed: CustomExtension model, custom runtime registration, ChatToolFactory, ChatService approval/resumption path, Assistant model, ChatRequestFactory, Builder, SkillManager/BuiltinSkills, and assistant detail view-model. Historical tool-creator skill and assistant Extensions page were inspected as references only.
- The existing generic approval contract is `Tool.needsApproval`; ChatMessageTools and ChatService already render and handle its approval flow. The custom runtime's extra session-grant mechanism is redundant and is the part to remove.


## 41. Stage 4/5 approval and AI-assistance correction — completed and verified (2026-11-05)

### Final behavior

- Replaced `CustomExtension.permission: ExtensionPermission` with `CustomExtension.needsApproval: Boolean = true`. The old enum and its validation branch are removed.
- Existing settings use `JsonInstant` with `ignoreUnknownKeys = true`. The legacy serialized `permission` field is therefore ignored, and the new default safely requires approval. A focused regression test decodes a legacy `ALWAYS_ALLOW` record and verifies `needsApproval == true`.
- Runtime custom tools now set the standard `Tool.needsApproval` callback directly from the saved boolean. ON invokes the existing chat approval flow for every call; OFF does not prompt. No custom approval card, conversation grant, or alternate approval path remains.
- Removed the custom-only session-grant map and resolver from `ChatService`, and removed the grant parameters from `ChatRequestFactory` and `ChatToolFactory`. The existing `ChatService.handleToolApproval`, `ToolApprovalState`, and chat UI approval components remain the only approval implementation.
- The Builder now shows one Material `Switch` labelled “Need approval”; it is independent from the separate Enable checkbox. New tools default to approval required.
- Simple and Advanced both display AI-assistance guidance. The AI may draft/refine all fields (including instructions, schema, JavaScript and test input) in either mode. The user places/reviews the generated parts in the Builder and runs the real sandbox test; AI assistance never marks a test successful, saves, enables, assigns, or changes the approval switch.
- Existing QuickJS sandbox, tool-name fingerprinting, schema validation, argument limits, assistant assignment, and test-before-enable checks remain unchanged.

### Files changed for this correction

- `app/src/main/java/me/rerere/rikkahub/data/model/CustomExtension.kt`: one approval boolean replaces the enum; obsolete permission validation removed.
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionTools.kt`: remove grant resolution and use the shared Tool approval callback.
- `app/src/main/java/me/rerere/rikkahub/data/ai/tools/ChatToolFactory.kt` and `app/src/main/java/me/rerere/rikkahub/data/ai/ChatRequestFactory.kt`: remove custom grant plumbing.
- `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`: remove the custom grant map and resolver calls; retain generic approval/resumption unchanged.
- `app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderPage.kt`: replace four radio choices with one standard switch; provide AI guidance in both modes.
- `app/src/main/res/values/strings.xml`: replace obsolete permission labels with the one-toggle and dual-mode AI guidance copy.
- `app/src/test/java/me/rerere/rikkahub/data/model/CustomExtensionTest.kt`: cover default approval, independent approval preference, and safe legacy JSON decoding.
- `app/src/test/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionToolsTest.kt`: replace four-mode/session-grant tests with ON/OFF assertions against the shared Tool callback; retain selection, validation, collision, sandbox and input-bound tests.
- `app/src/test/java/me/rerere/rikkahub/ui/pages/extensions/ExtensionBuilderStateTest.kt`: remove obsolete enum references; retain Builder test-fingerprint, validation and input-safety coverage.
- This plan: record the approved correction, supersede historical permission descriptions and record verification.

### CI attempts and final result

- Run 42044223234 failed at Android resource merge because the approval-help string contained an unescaped apostrophe. The wording was changed to avoid the resource-compiler issue.
- Run 42044810216 compiled the app but failed while compiling unit tests because `ExtensionBuilderStateTest` still referenced the removed `ExtensionPermission` enum. The test was updated to the new model.
- Final run: [GitHub Actions 42045558689](https://github.com/Cutie-Luna/LastNap/actions/runs/42045558689), commit `5af7cafa60830e1a8231d6f9cb7319393bcf5018`. Overall run and `persistence-tests` job succeeded. Logs confirm `:app:compileGithubDebugKotlin`, `:app:testGithubDebugUnitTest`, and `BUILD SUCCESSFUL in 2m 42s`.
- The six focused classes passed: `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, `SettingsStoreTest`, `CustomExtensionToolsTest`, and `ExtensionBuilderStateTest`.
- The Web UI emitted the same non-fatal sourcemap-location warnings. No lint, full assemble, signed APK, APK verification, install, or device screenshot was run.

### Stage 4/5 disposition and Stage 6 gate

- **Stage 4/5 source correction and focused CI acceptance: complete.** The user-approved one-toggle approval design and AI assistance in both Builder modes are implemented and the focused compile/tests pass.
- **Phone visual acceptance remains open** and is still deferred to the Stage 8 build/device path. This is not being represented as device-tested.
- Stage 6 may now begin. The approved scope is the built-in assistant-scoped Tool Creator skill, assistant-specific Custom Tools selector/tab, and direct Builder entry point, as defined in Section 40. Do not alter the generic approval UI or unrelated RikkaHub screens.


## 42. Stage 6 Tool Creator skill and assistant integration — source review and focused CI complete (2026-11-05)

### Source review on `lastnap-clean-foundation`

- Source head verified before this plan update: `9c3245da52fa0754dbc1d26fb75097b9cd2d48f4`.
- Added `app/src/main/assets/builtin_skills/tool-creator/SKILL.md`. The skill is explicit about opt-in behavior, supplies copy-ready content for every Builder field, and gives guidance for both Simple and Advanced modes. It leaves placement/review, sandbox testing, saving, enabling, assignment, and the Need approval choice under the user's control. It does not claim tests passed or invent sandbox APIs. Its approval guidance uses the single Need approval switch and the existing shared chat approval mechanism.
- `SkillManager.listSkills()` merges user and built-in skills, and `AssistantDetailVM` loads that list for the assistant's Extensions page. The Tool Creator skill is therefore discoverable through the existing skill selector without global prompt injection.
- `ChatToolFactory.createTools()` creates skill tools only when the current assistant's `enabledSkills` is non-empty; `createSkillTools()` further filters the available list to those exact enabled names. Its system prompt lists only those selected skills. The default `Assistant.enabledSkills` is empty.
- Added the Custom Tools tab to `AssistantExtensionsPage.kt`, backed by the existing `selectedCustomExtensionIds` field. It lets the user select only tools that are enabled and pass current model validation, and its manage/empty-state actions navigate directly to `Screen.ExtensionBuilder`.
- `CustomExtensionsContent` disables unready tools and never toggles them into the assistant selection. `createCustomExtensionTools()` independently enforces assistant ID selection, enabled state, validation, and current test fingerprint at runtime; UI selection is not treated as a security boundary.
- `RouteActivity.kt` registers `Screen.ExtensionBuilder`; the global Extensions hub retains its Custom Tools entry. Existing assistant extension tabs and the global entry remain available.
- Updated `ExtensionSelector.kt` with a Custom Tools tab for the reusable selector component. The dedicated assistant Extensions page is the verified active entry point for selection and Builder navigation.
- Updated English base strings for the new tab, statuses, Builder guidance and empty states. Other locale files remain Stage 7 work.
- No custom approval dialog, duplicate grant/session mechanism, global skill injection, automatic test success, auto-enable, or automatic assistant assignment was introduced.

### Focused test coverage and CI result

- `BuiltinSkillsTest` verifies the Tool Creator asset exists, built-in skill assets have valid metadata, local skills override built-ins by name, and new assistants have no enabled skills by default.
- `SkillsToolsTest` verifies no skill tool is exposed when no skill is enabled and that enabling Tool Creator exposes only that skill's instructions.
- `CustomExtensionToolsTest` verifies only selected, enabled, valid, currently tested definitions become callable, including the assistant-selection exclusion case and shared Need approval callback.
- Existing `ExtensionBuilderStateTest` continues to verify the test-before-enable fingerprint and safe test-input construction.
- Final focused CI run: [GitHub Actions run 42052942804](https://github.com/Cutie-Luna/LastNap/actions/runs/42052942804), branch `lastnap-clean-foundation`, source head `9c3245da52fa0754dbc1d26fb75097b9cd2d48f4`. The `persistence-tests` job and its “Run focused custom-tool model and runtime tests” step completed successfully.
- The log confirms `:app:compileGithubDebugKotlin`, `:app:testGithubDebugUnitTest`, and `BUILD SUCCESSFUL in 2m 58s`. Eight focused test classes ran: `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, `SettingsStoreTest`, `CustomExtensionToolsTest`, `ExtensionBuilderStateTest`, `BuiltinSkillsTest`, and `SkillsToolsTest`.
- This is focused compile/unit-test verification only. It is not a full test suite, lint, complete APK build, signed release, install, or device/visual test.

### Stage 6 disposition and next work

- **Stage 6 source implementation and focused CI acceptance: complete.**
- **No action is required from the user now.** The Tool Creator skill must be explicitly enabled for the desired assistant when a suitable build is available; no assistant has been silently opted in.
- Stage 7 remains next for locale completeness and integration polish. Stage 8 remains responsible for full build, lint, full tests, APK/signature checks, and phone review. Do not claim the Builder's phone layout or runtime navigation has been device-verified.

## 43. Stage 7 locale audit and implementation plan — 2026-11-05 (pre-edit)

- Starting source head: `2f39c5922a9522edc4bc0bfa213e1a603cbfe9e9`; current branch and full recursive tree were rechecked against upstream before Stage 7.
- The English base resource contains 80 LastNap custom-tool / Tool Builder string keys that are not yet present in any of the seven other supported app locales (`values-ar`, `values-de`, `values-ja`, `values-ko-rKR`, `values-ru`, `values-zh-rTW`, `values-zh`). This includes Builder labels, help, validation/test/enablement states, approval-switch text, Custom Tools assistant selection/status text, and the global Extensions card.
- The historical `feature/extensions-foundation` branch has translations for some older variants, but several are semantically stale (the older UI described a future AI feature, used four permission choices, and called tools generic extensions). Do not copy those stale values wholesale. Preserve the translated wording only where it still accurately matches current LastNap behavior; update all product-facing text to the current Custom Tools / Tool Creator / single Need approval model.
- Planned changes: only the seven app-module locale `strings.xml` files. Add every missing base key, retain the English base resource unchanged, preserve unrelated translations and ordering, and keep formatting placeholders such as `%1$s` intact. Do not edit OAuth/Search/Web UI locales in this pass because they have no corresponding new Stage 7 keys.
- After locale edits, compare all 80 added keys against the base resource in all seven locales, verify duplicate-free resource names and matching format placeholders, then review LastNap shortcut/manifest action IDs and settings backup inclusion for the custom-tool and assistant-selection fields.
- No source edits are to be made until this scope has been documented. Do not combine unrelated identity or UI redesign work into Stage 7.

## 45. Stage 8 full verification workflow — pre-edit audit and design (2026-11-05)

### Repository and build audit

- Starting branch/head: `lastnap-clean-foundation`, `00a14f04b7a6fa901d953f86f624570a9e7ffaf2`.
- Rechecked protected refs: LastNap `master` remains `903e058b9361bff71d0b79f4ef2d8ac23d6597e6`; historical `feature/extensions-foundation` remains `942c1b49019dbbd55f02606f31b74cc258c14641`. Neither is a work target.
- Re-fetched complete recursive trees against live upstream `rikkahub/rikkahub` master `f958af6a1a4a16dd8c1ac4138662a838b590e6c5`. Both responses are non-truncated: LastNap 1,550 blobs, upstream 1,538; 14 LastNap-only paths, 2 unrelated upstream-only sponsor paths, 68 differing shared paths. The tree matches the post-Stage-7 audit; no unexplained source path was introduced by the locale changes.
- Current workflows inspected: `.github/workflows/stage3-persistence-tests.yml` runs the focused regression suite; `.github/workflows/daily-build.yml` is manual and has `contents: write`, secret-backed signing/Google Services setup, and an optional nightly prerelease publication input. Do not broaden or repurpose that publishing-capable workflow for verification.
- Current Android configuration in `app/build.gradle.kts`: application ID `com.lastnap.app`, versionName `3.1.3`, versionCode `196`, GitHub distribution flavor, optimized release build type, and ABI splits for `arm64-v8a` and `x86_64` plus a universal output. Build settings will remain unchanged for this stage; the signed artifact exposed to the user will be the ARM64 split only.
- Release signing reads `local.properties` and expects the key file created at `app/app.key`. `.gitignore` currently excludes `local.properties` but does not explicitly exclude `app/app.key`; add that path before introducing a workflow that materializes the key in a runner.

### Chosen verification workflow

- Add a dedicated `.github/workflows/stage8-verification.yml`, triggered by pushes to `lastnap-clean-foundation` and by manual dispatch. Grant `contents: read` only. Do not create, update, or publish a GitHub Release.
- Separate the non-secret verification job from the signed release job. The verification job creates only the same temporary CI dummy Google Services configuration used by the existing focused workflow (debug package `com.lastnap.app.debug`), runs all app GitHub-debug unit tests plus every Android-library debug unit-test task, runs Android lint for the GitHub debug variant, and assembles the GitHub debug APK.
- Only after the verification job succeeds, a release-artifact job consumes the existing repository secrets `KEY_BASE64`, `SIGNING_CONFIG`, and `GOOGLE_SERVICES_JSON`. It writes them to runner-local files using environment variables without printing values, assembles `:app:assembleGithubRelease`, and fails safely if any required secret is absent.
- Inspect the single split APK matching `arm64-v8a`: verify package `com.lastnap.app`, versionName `3.1.3`, versionCode `196`, that its packaged native libraries contain only `arm64-v8a`, that expected launcher/notification resources are present in the APK resource table, and that `apksigner verify` succeeds. Record SHA-256 and the public signing-certificate digest in a metadata file. Upload only the ARM64 split APK and non-secret verification metadata/reports as workflow artifacts; do not upload the keystore, local.properties, or Google Services file.
- Leave the app Gradle ABI configuration and the existing manual daily-build/release workflow unchanged. The user will receive a signed APK artifact for review, not an automatically published release.

### Risks and acceptance

- The build runner receives existing signing and Google Services secrets only in the signed job, after tests/lint pass. Secret values must never be printed, included in metadata, uploaded, or committed. The runner-local `app/app.key` is added to `.gitignore`.
- The Gradle release task may also create x86_64 and universal intermediate APKs because that is the current configured split policy; only the ARM64-specific split is validated as the deliverable and uploaded. Do not describe other APKs as the user deliverable.
- If the signing or Google Services secrets are unavailable or malformed, the signed-release job must fail with a safe missing-configuration message and no artifact; do not substitute a debug/unsigned APK or weaken signing requirements.
- Acceptance requires successful full tests, Android lint, debug APK assembly, signed optimized GitHub release assembly, ARM64/package/version/resource checks, signature verification and artifact upload. A matching historical signing-certificate comparison is only possible if a trusted previous APK is available; actual update/install behavior and phone UI/icon observations remain separate user-device checks.

## 46. Stage 8 lint findings — 2026-11-05 (pre-edit)

- Corrected full verification run 42060671572 on `6be786e34c99aaa9f0bad325ec72d05cf712b075` failed only at `:app:lintGithubDebug`; its focused regression sibling 42060671392 passed.
- Full Gradle reports show 1,132 tests, zero failures, one skipped document-module test. The app report has 524 tests, zero failures or skips. `:app:assembleGithubDebug` completed and produced `app-github-arm64-v8a-debug.apk` before lint failed.
- Lint reported 56 errors, 392 warnings and 1 hint. 53 errors are inherited from upstream: 50 `LocalContextGetResourceValueCall`, two `ContextCastToActivity`, and one `NonObservableLocale`. The reported lines are in byte-identical upstream files, except `ImportExportTab.kt` and `Mermaid.kt`, whose only differences are LastNap filename/watermark lines elsewhere in each file.
- Three `MissingTranslation` errors concern the recently synchronized upstream compression strings: `chat_page_compress_keep_recent`, `chat_page_compress_keep_recent_desc`, and `chat_page_compress_not_enough_messages`, missing in Arabic, Russian, Japanese and Korean. The 80 LastNap Tool Builder keys remain complete in all seven non-English locales.
- Before further edits, the chosen minimal fix is to add only those three translations to `values-ar`, `values-ru`, `values-ja` and `values-ko-rKR`; add an app lint baseline containing only the 53 reviewed inherited upstream error findings; and configure that baseline in `app/build.gradle.kts`. Do not baseline warnings or missing translations, disable lint, lower severity, or set `abortOnError = false`.
- Re-run full tests, lint and debug assembly. Only after that job succeeds may the dependent signed ARM64 job run. No publishing or device installation is authorized by this workflow.

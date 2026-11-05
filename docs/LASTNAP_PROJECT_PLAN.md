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

- Review the existing Agent Skill loading/enablement rules.
- Add `tool-creator` alongside `skill-creator`.
- Keep it disabled until explicitly enabled for the relevant assistant.
- Ensure its instructions are not injected globally.
- Add or restore the assistant-specific custom-tool selection UI.
- Add a direct Tool Builder entry point only within the relevant assistant's custom-tools area.
- Verify assistants without the skill/tools do not receive their instructions or callable definitions.
- Ensure roleplay/general assistants remain unaffected unless the user assigns the feature.

**Acceptance:** the feature is opt-in and assistant-scoped end to end; unrelated assistant experiences remain unchanged.

### Stage 7 — Locales, shortcuts, and integration polish

**Goal:** finish only the user-visible resource and integration work required by the implemented feature.

- Port only the required new strings to the supported locale files.
- Check every resource key exists in the base locale and supported translations; do not rewrite unrelated translations.
- Verify launcher shortcut actions, manifest filters, and route constants together.
- Verify the app label, launcher icon, round icon, monochrome icon, notification small icon, and app links.
- Confirm settings backup/restore behavior includes the new persisted fields if applicable.
- Record each resource or existing-screen file touched and its exact reason.

**Acceptance:** feature labels are complete, shortcuts resolve, backup/restore remains safe, and no unrelated UI has been altered.

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

## 8. Current next action

Stages 0 and 1 are complete; Stage 2 source identity/artwork acceptance is closed on the documented bounded-scan basis in Section 30. Stage 3's assistant-scoped custom-tool persistence foundation and its four focused unit-test classes passed on GitHub Actions (run 42028485101, commit `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c`). Stage 4's pre-edit runtime and permission design is recorded in Section 36; its source implementation and runtime regression tests are the current work. APK/package-table and Android 13 device proof remain assigned to Stage 8; they are not claimed complete.

When Stage 5 arrives, redesign the add-tool popup as a spacious page-like experience while preserving Simple and Advanced and all existing functionality. This is a UI presentation change, not permission to simplify away features.

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

## 35. Focused Stage 3 test-runner and successful execution — 2026-11-05

- Added `.github/workflows/stage3-persistence-tests.yml` on `lastnap-clean-foundation`. It runs on pushes to that branch (and supports manual dispatch), uses JDK 21 / Node 22 / pnpm 11, creates a CI-only dummy `app/google-services.json`, and runs only the four focused persistence/model test classes through `:app:testGithubDebugUnitTest`.
- Safety review of the workflow: `permissions: contents: read`; no signing secrets or keystore access; no release/assemble task; no artifact upload or publication. The dummy Google Services file exists only in the ephemeral runner workspace.
- First run: [GitHub Actions run 42028485101](https://github.com/Cutie-Luna/LastNap/actions/runs/42028485101), event `push`, branch `lastnap-clean-foundation`, commit `9d1cb5a1f0a384ce9fa54c16ce352af9017b659c`, run attempt 1. Overall run and sole `persistence-tests` job both completed successfully.
- The log confirms all setup steps completed, Gradle compiled the GithubDebug app and unit-test sources, then `:app:testGithubDebugUnitTest` completed with `BUILD SUCCESSFUL` in 4m 43s (228 actionable tasks). The four filters were `CustomExtensionTest`, `SettingsNormalizationTest`, `SettingsFieldsTest`, and `SettingsStoreTest`. The log contains non-fatal existing deprecation/opt-in warnings; no test or compile failure.
- **Disposition:** Stage 3 acceptance is met and Stage 3 is complete. The focused workflow remains as a reusable non-publishing regression check. This result is not a lint result, full APK build, signing verification, install, or device test.


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
- On approval, inspect the pending tool call's exact generated name and match it against the currently selected, enabled, valid THIS_SESSION extension. Only an explicit approval of that current definition adds a grant. If the extension was edited, removed, disabled, or deselected while approval was pending, do not grant it; the old versioned tool name will not resolve to the new definition.

### Intended source/test scope

- New app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/CustomExtensionTools.kt for registration, safe name generation, schema wrapping, permission predicates, input validation, and QuickJS execution.
- Minimal integration in ChatToolFactory.kt, ChatRequestFactory.kt, and ChatService.kt to pass assistant-scoped tools and per-conversation session grants. Do not change AgentLoop, Tool, built-in tool implementations, navigation, or unrelated RikkaHub screens unless a concrete test proves an unavoidable requirement.
- New focused CustomExtensionToolsTest.kt covering assistant selection, disabled/untested/invalid definitions, all four permission modes, session grants, versioned names, name collisions, schema preservation, JSON-object/UTF-8 input limits, and execution through the existing sandbox. Add the smallest practical integration tests for grant recording/resumption if existing service test seams permit it.
- Acceptance: only the current assistant's selected, enabled, valid, tested custom tools are offered; stale definitions cannot execute after edits; permissions behave exactly as defined above; the current sandbox remains the only execution environment; existing built-in tool behavior is unchanged.

### Stage 4 risks and verification limits

- THIS_SESSION is explicitly defined here as a conversation-scoped, in-memory grant lasting for the app process, rather than a persisted permission or a short-lived ConversationSession object (which is evicted after an idle timeout). This avoids surprising re-prompts when navigating away and back while still resetting grants after process restart.
- A model-provided schema is a contract for tool-call generation, not a security boundary. Runtime must still validate the actual JSON object and byte limit. Do not claim full JSON Schema validation unless a complete validator is implemented and tested.
- Focused unit tests can validate the pure registration/permission/input logic; they do not replace provider-specific schema compatibility or device verification. Keep Stage 7 integration/regression and Stage 8 build/device gates open.
- No Stage 4 source files have been changed yet. This design is recorded before implementation; next step is to implement only the listed runtime integration and focused tests.

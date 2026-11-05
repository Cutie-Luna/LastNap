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

Stage 0 and Stage 1 are complete. Stage 2 identity/artwork implementation and the follow-up OAuth branding correction are applied on `lastnap-clean-foundation`. The next action is to complete a reliable repository-wide literal search for legacy package/action/scheme/brand references; keep Stage 2 open until that is done. APK/package-table and Android 13 icon proof remain assigned to Stage 8. Do not start Stage 3 or the Tool Builder UI redesign before the Stage 2 source audit is closed.

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
- [ ] Complete a reliable repository-wide literal search for legacy package/action/scheme strings. GitHub code search returned zero results with incomplete_results=true, and local git clone failed because this environment could not resolve github.com; manual checks of all known identity entry points passed, but this exhaustive search is not claimed complete.
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

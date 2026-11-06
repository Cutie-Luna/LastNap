# LastNap — Custom Tools and Inline Media Execution Plan

**Created:** 2026-11-06  
**Working branch:** `feature/custom-tool-api-runtime-next`  
**Protected baseline:** `lastnap-clean-foundation` at `8e4ac854df5d15d35a9f7853698ebf4dfe8e0653`

> This is a new, standalone execution plan. It does not replace, edit, renumber, or mark complete any stage in `docs/LASTNAP_PROJECT_PLAN.md`. The existing plan and its historical stages remain untouched. All implementation work described here is confined to `feature/custom-tool-api-runtime-next`. The pre-existing `feature/custom-tool-api-runtime` branch is a frozen source point for this continuation and must not be modified.

## Mission

Complete the user's intended custom-tool experience, then add inline Markdown video playback. Custom tools must be able to call real user-configured HTTP APIs through a narrow native host bridge, use named Secrets and Variables, optionally persist data in an isolated folder owned by each tool, and return media that the chat/model can actually consume. The existing Tool Builder remains the place where the user configures, tests, enables, and assigns tools.

After the custom-tool capability is complete and regression-tested, implement the agreed borderless, inline, YouTube-like Markdown video player.

## Non-negotiable boundaries

- Never modify `lastnap-clean-foundation`, `master`, or `feature/extensions-foundation`.
- Do not modify `feature/custom-tool-api-runtime`; it is the frozen source branch from which this continuation was created.
- Work only on `feature/custom-tool-api-runtime-next`.
- Do not modify the old project plan or its stages.
- Do not redesign or refactor unrelated app features, navigation, settings, shared styling, performance, or integrations.
- Preserve existing RikkaHub behavior, SSH SecretCipher behavior, Custom JS search behavior, existing custom-tool approval flow, and existing assistant-scoped assignment.
- No broad dependency upgrades. Add a dependency only when the selected implementation demonstrably requires it.
- No secrets or secret ciphertext in tool definitions, Settings exports, database backups, tool exports, prompts, JavaScript, test fixtures, logs, diagnostics, or tool results.
- QuickJS receives no direct Android, filesystem, Node, browser, process, or unrestricted network access. All capabilities are explicit, opt-in, and mediated by narrow native interfaces.
- The single existing per-tool **Need approval** switch remains the only approval control. Do not create another approval system.
- Storage is per tool, not per assistant or App Manager. It is shared across assistants that use the same tool and created lazily on first successful write.
- Do not begin video-player implementation until the custom-tool completion gate passes.
- Never claim a test, build, APK verification, installation, or device behavior that has not actually been observed.

## Stage 0 — Establish an isolated continuation workspace

**Goal:** preserve the existing working branch and continue from an exact, verified snapshot on a new branch.

Tasks:
- Create `feature/custom-tool-api-runtime-next` from the current tip of `feature/custom-tool-api-runtime` without moving or modifying the source branch.
- Verify both branches point to the same commit immediately after branch creation.
- Record the source commit SHA and the new branch name.
- Confirm the existing project plan and all inherited files are unchanged by branch creation.
- Keep all subsequent commits and edits exclusively on the new continuation branch.

Acceptance:
- `feature/custom-tool-api-runtime-next` begins at source commit `5178056124ba9728f9edb63f5d668bb52117f561`.
- The source branch remains at that same commit until the user explicitly authorizes otherwise.
- Protected branches remain untouched.
- No source, resource, workflow, dependency, or old-plan change is made as part of branch creation.

## Stage 1 — Complete the architecture audit and freeze contracts

**Goal:** remove design ambiguity before source edits.

Inspect current source and direct call sites for:
- Custom tool model, persistence, validation, test fingerprint, Builder state/page, runtime dispatch, QuickJS lifecycle, DI registration, and built-in Tool Creator skill.
- Existing QuickJSFetch and its Custom JS search caller, OkHttp client ownership, cancellation, redirects, and coroutine lifecycle.
- SecretCipher, Android Keystore behavior, backup and restore paths, Settings serialization, manual backup, and tool import/export.
- FilesManager, FilesRepository, managed-file records, file URI handling, deletion, and app-private storage.
- UIMessagePart media variants, provider tool-result serialization, vision modality checks, chat rendering, and Markdown image rendering.
- Existing Media3/ExoPlayer dependencies, player components, Markdown parser, local thumbnail support, and lifecycle handling. This is audit only; no video implementation yet.

Freeze and document these contracts:
1. Persisted opt-in capability flags with safe defaults for existing tools.
2. A single documented named-reference convention shared by Simple and Advanced modes.
3. Tool-declared Secrets and Variables, with required/optional status and descriptions, namespaced to the immutable tool identity by default.
4. Dedicated CRUD experience for named values. Secret values are masked and never read back into Builder code or AI context.
5. Native request-time secret resolution. Do not provide a generic plaintext secret getter to JavaScript. Define supported injection locations and reject unsafe/ambiguous substitutions.
6. Missing required values are clearly identified before a tool can be enabled or invoked.
7. HTTP method, headers, query, JSON/text/binary request bodies, response status/headers, JSON/text/binary response access, timeouts, cancellation, redirect policy, URL policy, response-size limits, and error shape.
8. Explicit opt-in enforcement at runtime, not just in the Builder UI.
9. Per-tool storage API, safe relative names, quotas, size limits, lazy directory creation, and cross-tool isolation.
10. Retention and deletion behavior for tool removal, storage disablement, tool re-import, and app backup/restore.
11. Test fingerprint inputs: all executable code/schema/capability declarations that affect execution, but never secret values.
12. Regression matrix proving Custom JS search and SSH credential flows remain unchanged.
13. Explicit ownership for Secrets/Variables: tool-scoped values are removed with their owning tool; any future cross-tool shared value requires explicit sharing/ownership records and must not be deleted while another owner still references it.

### Media ownership and lifecycle contract

This contract is separate from tool-owned storage and is mandatory for every supported media type (initially images; later any audio/video/document type only when fully supported):

- **Conversation media belongs to the conversation, never to the producing tool.** Once a generated result has been committed to a chat message, deleting, disabling, editing, or re-importing the originating tool must not invalidate that result.
- Store generated chat media through a conversation-owned managed-media path/record, not inside the tool's private storage directory. Tool storage is temporary/private working data; chat media is durable user-visible output.
- Associate each media reference with its owning conversation and message. Deleting one conversation removes its messages and all media references/assets owned only by that conversation. It must not affect any other conversation, even if another message references the same underlying bytes.
- If physical blobs can be shared/deduplicated, use explicit ownership/reference records and delete a blob only after its final live reference is removed. Otherwise use independently owned copies. Never infer ownership from filename, tool ID, URL, or matching content.
- Persisted message deletion, regeneration, or editing that removes media references, and conversation deletion, must clean up only media no longer referenced by any surviving message. Model context-window truncation or summarization must never delete media still attached to a persisted message. Cleanup must be idempotent and safe after crashes.
- Tool deletion must never cascade into conversation messages, media references, or conversation-owned media files.
- Treat media publication as an atomic boundary: a result is either committed to its target message with durable media ownership, or remains temporary and is cleaned up. No half-published message, dangling reference, or permanent orphan is acceptable.
- If a tool is deleted while executing, block new invocations and cancel/invalidate in-flight work. Any output already atomically committed to a conversation remains; uncommitted temporary output is discarded. A late callback must not resurrect a deleted tool or publish an unowned file.
- Chat backup/restore must preserve the relationship between messages and their media, or explicitly report missing media and render a safe placeholder. Tool deletion must not change chat backup contents. Tool exports must not contain conversation media. Explicit user export/save is a separate copy operation and must not change the ownership of the chat's original media.
- When a conversation is deleted, remove its associated media from managed storage and metadata without touching media belonging to other conversations. If the same underlying blob has references in multiple conversations, remove only the deleted conversation's references and retain the blob until no references remain.
- Failed cleanup must be recoverable: mark pending deletion, retry safely, and never expose deleted media in another chat. Avoid deleting files before the metadata transaction establishes that no live owner remains.
- If an API returns a temporary or signed media URL rather than bytes, materialize it into conversation-owned managed storage before publishing the message, using bounded downloads and the same URL, redirect, and private-network protections as the API bridge. Do not treat an expiring remote URL as durable chat media. If safe materialization fails, surface a clear tool error rather than publishing a result that is expected to disappear.
- Media generated by a tool must be copied or ownership-transferred into conversation-managed storage before its tool-owned temporary file can be cleaned up. A chat message must never point directly into a tool's private storage namespace.
- Deleting a conversation cancels or invalidates any still-running work targeting that conversation. Late callbacks cannot recreate its messages, media records, or files; uncommitted temporary output is cleaned up.
- Explicitly saving/exporting a chat image to a user-owned library or external destination creates a separate user-owned copy. That deliberate copy survives later deletion of the conversation; merely viewing or generating the image does not create such a copy.
- Remove conversation-specific thumbnails, previews, and cached derivatives when their owning media is no longer referenced. Shared derivatives follow the same ownership/reference rules as the source media.

Acceptance:
- Tests prove tool deletion leaves old chat media readable.
- Tests prove deleting chat A removes its unshared media but leaves chat B and its media readable, including a shared-blob/reference-count scenario if deduplication exists.
- Tests prove explicitly saved user-owned copies survive conversation deletion, while ordinary chat-owned media does not.
- Tests cover message-only deletion/regeneration, repeated deletion, deleting a chat during an active media-producing call, process death between metadata/file operations, tool deletion during an active media-producing call, and backup/restore consistency.
- Tests verify temporary URL results are materialized and remain readable after URL expiry or tool deletion.

### Stage 1 audit findings

The following source facts were verified on the continuation branch's inherited source snapshot:

**Custom tool lifecycle and execution**
- `CustomExtension` definitions are stored in `Settings.customExtensions`; each assistant separately stores `selectedCustomExtensionIds`. `Settings.withoutInvalidReferences()` filters stale IDs, and the existing `Settings.removeCustomExtension(id)` transformation removes the definition and its assignment from every assistant in one Settings update.
- `Tool.execute` is a suspend function. `ChatToolFactory.createTools()` builds the custom tools for each generation, and `ChatRequestFactory.createContext()` already has the conversation ID but does not currently pass it to `ChatToolFactory`. The new tool-media path must pass the target conversation ID explicitly.
- The custom-tool runtime currently returns text only through `evaluateJavascript`. It does not receive `fetch`, storage, secret resolution, or media-save capabilities.
- Builder tests currently call `evaluateJavascript` directly. Once capabilities exist, Builder tests and chat execution must invoke the same capability-aware runtime and policy, with isolated test outputs and no test-only bypass.
- Tool names already bind to the immutable tool ID and tested-definition fingerprint. New capability declarations and allowed-host policy must be included in that fingerprint; secret values and ordinary variable values must not be.

**QuickJS and network**
- `QuickJSFetch.kt` is a synchronous OkHttp-backed bridge injected only by `CustomJsSearchService`. It returns status and text/json body only; it has no response headers or binary response path and currently reads the full response body into memory.
- The search bridge's existing behavior is a compatibility contract. Do not silently change it to serve custom tools. Build a separate custom-tool bridge/runtime adapter and add regression tests proving Custom JS search remains unchanged.
- The shared OkHttp client is configured in `DataSourceModule` with app proxy settings, redirects enabled, a 20-second connect timeout, a 10-minute read timeout, and a 120-second write timeout.
- That client also has a `RequestLoggingInterceptor` which records request headers and request bodies, redacting only `Proxy-Authorization`. Its `HttpLoggingInterceptor` is configured at HEADERS level and also redacts only `Proxy-Authorization`. API credentials in an `Authorization` or custom header could therefore be logged if custom tools used this client. Custom-tool requests must use a dedicated client with no request/body/header logging interceptors and must redact sensitive values from all surfaced errors.
- The dedicated client does not inherit LastNap's app-level proxy selector/authenticator or any app-wide logging, AI request, or search behavior. It uses the device network routing (including the system VPN) directly so destination DNS validation applies to the actual connection. Use shorter bounded timeouts, bounded request/response streaming, explicit redirect handling, and DNS/address validation.

**Secrets, settings, storage, and backups**
- Existing `SecretCipher` uses AES-GCM with Android Keystore alias `rikkahub_secret_cipher` for SSH credentials. Its ciphertext is device-bound. Preserve the class, alias, data model, and all SSH call sites unchanged.
- Custom tool Secrets will use a separate AES-GCM Keystore alias and a dedicated store under `noBackupFilesDir`. Bind ciphertext to both immutable tool ID and secret name (authenticated additional data) so values cannot be swapped between tools/names. The store must be atomic, masked, and never expose plaintext through a getter to JavaScript or AI.
- Settings are persisted by Preferences DataStore and the app's manual `BackupManager` serializes the Settings model. Tool definitions and non-secret Variables can be backed up with Settings. Tool definition export/import is not currently implemented in the audited source; when added, export only the definition/declarations, not local values or Secrets, and assign a fresh tool ID on import.
- Android Auto Backup is enabled. The legacy `backup_rules.xml` currently includes `upload/`; Android 12+ `data_extraction_rules.xml` has empty/commented cloud and device-transfer rules. Custom-tool private data should live under `noBackupFilesDir`, rather than depending on fragile exclusion rules.
- Manual `BackupManager` currently includes `upload`, `skills`, `fonts`, and `media_creation`; only Skills and Media Creation are treated as nested folders. If a dedicated conversation-media folder is used, add it to the manual backup folder set and nested-folder set, plus the legacy Android backup include rule. Restore validation must accept only paths under that explicit folder.
- Variables are ordinary, non-secret values. Keep their local values separate from the tool definition, keyed by tool ID/name, and include them in the app Settings backup. Do not include them in an exported tool definition. Secrets and per-tool storage are not backed up.
- Per-tool storage will live under `noBackupFilesDir/custom_tool_storage/<toolId>/`; it is lazily created, inaccessible by arbitrary path, and removed only by explicit tool deletion. Disabling storage blocks reads/writes but retains the data until deletion or re-enabling. Restore/re-import must never silently regain an old tool's storage namespace.

**Conversation media and deletion**
- `Conversation.files` walks all message nodes and all branches. The local-file collector also descends recursively into nested `UIMessagePart.Tool.output` and includes Image, Video, Audio, and Document `file://` URLs.
- `Conversation.fork()` copies local file URLs; forked chats therefore get independent copies rather than sharing the original file. Keep that isolation for custom-tool media and test that deleting either fork never removes the other's copy.
- `ChatService.deleteConversation()` uses `ConversationSession.replace()`, which cancels and joins the session's active jobs before replacing its state and deleting the persisted conversation. This is the existing foundation for preventing late tool work from publishing into a deleted conversation.
- `ConversationRepository.deleteConversation()` deletes conversation/message rows in a Room transaction, then checks the global persisted message-node index before asking `FilesManager.deleteChatFiles()` to remove unreferenced files. The file helper now accepts only canonical, non-symlink paths under the managed `UPLOAD` folder (not arbitrary `file://` paths such as workspace or skill assets), deletes disk files immediately, and removes managed-file rows asynchronously. A process death or I/O failure can still leave an orphaned file or stale managed-file row; cleanup cannot depend on a single best-effort callback.
- Message edits/deletes persist new message nodes and then remove files only after checking global persisted message-node references and active session state/queued attachments. `findUnreferencedChatFiles()` deduplicates candidates and conservatively retains them if the database lookup fails. Conversation deletion also passes active references from other sessions into repository cleanup. Keep these checks for every future media type; new conversation media must use unique per-conversation paths and cleanup must verify that no surviving message or active session references the file.
- Use the existing `managed_files` table rather than adding an owner table unless implementation proves it is necessary. Its `folder` and `relative_path` fields can represent a dedicated `conversation_media/<conversationId>/` namespace. Save generated assets there under random filenames, never in tool storage. Forking copies them into the fork's own conversation namespace.
- Add an idempotent startup reconciliation for this dedicated folder: retain files referenced by persisted message nodes; delete only confirmed unreferenced files and stale managed-file records. If cleanup cannot prove a file is unreferenced, keep it. This repairs crashes between Room changes and file deletion without scanning/deleting unrelated uploads.
- New API media must be saved before the tool returns the typed message part. If generation is cancelled before the output is committed, clean its temporary file; if a process dies in that interval, the startup reconciliation removes the orphan. Deleting a conversation cancels/joins its session first, then removes that conversation's media namespace and confirms no surviving reference points into it.
- Manual backup and Android backup must include conversation media with the database so message URLs remain valid. When a user chooses a database-only manual backup, missing media must render as unavailable placeholders instead of crashing the chat. An explicit Save/Export to a user-owned library creates a separate copy that survives conversation deletion.

**Model handoff and video**
- Typed `UIMessagePart.Image` is already rendered in generic tool results by `ChatMessageTools`. The Claude and Google provider adapters already encode image parts from local files; image understanding still depends on the selected model's advertised input modality. Stage 6 should deliver images only until the complete UI/provider path for another media type is verified.
- Markdown currently registers a custom image renderer but no inline video renderer. Media3 ExoPlayer and UI dependencies are already present in `app/build.gradle.kts` (version catalog Media3 1.11.1), so Stage 8 should audit and reuse them rather than add a dependency.
- The built-in Tool Creator skill accurately describes the current sandbox as having no network or filesystem access. Update it only after each advertised runtime capability is implemented and tested.

### Frozen design choices

1. **Network capability:** API access is disabled by default for existing and newly created tools. Enabling it is a separate capability toggle, not an approval setting. Each tool must also declare allowed host patterns; the native bridge enforces them on every request. HTTPS is the default. Loopback, link-local, private, and other non-public destinations are blocked unless the separate local-network capability is explicitly enabled. Resolve and validate addresses at connection time to resist DNS rebinding. Do not follow cross-origin redirects with credentials or downgrade HTTPS to HTTP; allow only safe same-origin redirects under the same validation rules.
2. **Credential handling:** support non-secret Variables in the JS runtime as a plain `variables` object. Expose a declared Secret only as an opaque reference token; resolve it natively at request construction and initially allow substitution only into explicitly declared HTTP headers. Never substitute a Secret into URL, query parameters, body, JS-readable values, logs, or error text. Missing required values prevent enablement/invocation.
3. **Network isolation:** custom tools use a dedicated direct OkHttp client. It does not use LastNap's app-level proxy setting, but it still follows device network routing and the system VPN. It contains no request logger, HTTP logger, AI interceptor, or inherited search behavior. Bound total execution time, connect/read/write durations, request size, text response size, and image byte size. Handle redirects explicitly and strip/reject credentials on any origin change.
4. **Tool value ownership:** declaration metadata lives with the tool definition. Non-secret Variable values live separately in Settings keyed by immutable tool ID/name and participate in the app Settings backup. Secret values are encrypted with a custom-tool-only Keystore alias under no-backup app-private storage, with tool ID and name authenticated as associated data. Tool export excludes local values; import assigns a new tool ID.
5. **Tool storage:** private storage is keyed by immutable tool ID under `noBackupFilesDir`, not `filesDir`, Settings, Room, or shared managed uploads. It is lazy, separately quota-limited, and only exposed through a narrow native interface. Disablement retains but blocks it; deletion removes it; export/backup never includes it.
6. **Conversation media:** generated image assets use `filesDir/conversation_media/<conversationId>/<randomName>` and existing managed-file metadata. No blob deduplication is introduced. Forks copy files to their own namespace. File deletion checks global persisted references and active sessions; a startup orphan sweep repairs interrupted cleanup. Conversation media is included when backups include files; missing files in database-only restores display a safe unavailable state.
7. **Deletion transaction:** tool deletion is a durable, idempotent lifecycle operation: mark the runtime tool unavailable, block new invocations, cancel/join active invocations, then atomically remove the definition, all assistant assignments, and its Variables with the existing Settings transformation. That Settings removal is the durable recovery marker; a separate tombstone is unnecessary because startup reconciliation can identify tool-owned stores whose immutable IDs are no longer present. Then delete only that tool's Secrets and private storage. Already-committed conversation media is not part of this transaction.
8. **Initial media support:** custom tools initially return typed images only. Audio/video/document outputs remain unavailable until their renderer, provider input adapter, backup path, and deletion lifecycle all pass end-to-end tests.

### Stage 1 audit acceptance
- Source facts and implementation contracts are recorded above before source edits.
- HTTP logging, credential leakage, cancellation, URL security, file ownership, backups, and recovery are explicitly addressed.
- No broad refactor or unrelated behavior change is required for the chosen implementation.

## Stage 2 — Implement the opted-in HTTP/API runtime

**Goal:** make custom tools capable of real HTTP API calls without granting network access to other scripts.

Tasks:
- Build a custom-tool-specific native QuickJS HTTP bridge. Reuse lower-level OkHttp/QuickJS patterns where useful, but leave the existing QuickJSFetch implementation and Custom JS search caller unchanged.
- Preserve Custom JS search's current contract and behavior; add focused regression tests around its caller.
- Add only the request/response features required by the frozen contract, including status/headers, JSON/text access, bounded binary response handling, and native image materialization without returning Base64 as model text.
- Pass the target conversation ID from ChatRequestFactory through ChatToolFactory into the runtime so media can be stored under the correct conversation namespace.
- Enforce opt-in capability at the host boundary.
- Enforce allowed-host policy, DNS/IP checks, HTTPS and local-network rules, timeout/cancellation, request/response size limits, safe redirects, response-header filtering, and clear redacted errors.
- Ensure HTTP response metadata and binary data are not silently converted into lossy text.
- Add fake-server tests for supported methods, query/headers/bodies, response forms, errors, timeouts, cancellation, limits, allowed/blocked hosts, DNS/private-address checks, redirects, secret redaction, and disabled capability.

Acceptance:
- Opted-in tools can call a local fake HTTP server and receive correct structured results.
- Non-opted-in tools cannot access the bridge.
- Existing Custom JS search tests pass unchanged or with strictly additive regression coverage.
- No unrestricted host APIs are exposed to QuickJS.

## Stage 3 — Implement named Secrets and Variables

**Goal:** provide GitHub Actions / Cloudflare-style named configuration without leaking sensitive values.

Tasks:
- Add a dedicated local secret store, separate from Settings and database exports, using Android Keystore-backed encryption and app-private no-backup storage.
- Preserve the existing SecretCipher class, `rikkahub_secret_cipher` alias, SSH model, and SSH call sites. Use a separate custom-tool-only Keystore alias for API Secrets.
- Store ordinary Variables separately from sensitive Secrets; keep their non-secret values in Settings keyed by tool ID/name so they are included in app Settings backups but excluded from exported tool definitions.
- Add per-tool declarations of required and optional names, descriptions, and intended use.
- Add a dedicated masked management flow to create, update, and delete named values.
- Namespace values by immutable tool ID. Deleting a tool removes only values it owns; if cross-tool sharing is ever introduced, remove the deleted tool's reference and retain a shared value until its final owner explicitly releases it.
- Resolve secret references only inside native request construction. JavaScript, AI prompts, test output, diagnostics, logs, tool exports, and serialized definitions never receive the plaintext.
- Make missing required values visible and actionable.
- Handle reinstall/device restore by asking the user to recreate unavailable local Secrets; never silently substitute or export them. Local Variables may restore from Settings backup; per-tool storage and Secrets do not.
- Add tests for encryption/decryption, persistence, atomic writes, missing values, redaction, backup/export exclusion, and unchanged SSH behavior.

Acceptance:
- The user enters a Secret once under its declared name.
- Tool code references only the name.
- Secret plaintext never crosses into JS or model-visible content.
- Variables remain ordinary non-sensitive configuration.
- Existing SSH credentials continue to work.

## Stage 4 — Implement isolated optional per-tool storage

**Goal:** let a tool persist its own files/data without general filesystem access.

Tasks:
- Add a narrow native storage service that derives its private directory from an immutable tool ID under `noBackupFilesDir/custom_tool_storage/<toolId>/`.
- Expose only safe relative filenames and bounded read/write/list/delete operations that the product contract actually needs.
- Create the folder only on the first successful write when storage is enabled.
- Keep one folder per tool, shared across assistants using that tool.
- Enforce per-tool and global quotas, file-size limits, path traversal protection, and safe file types.
- Define storage disablement, tool deletion, import/re-import, and retention semantics. Disabling storage must not silently erase existing data.
- Tool-owned storage is not conversation media. Deleting a tool removes its own private storage and its own configuration/credentials according to the deletion contract, but never deletes files already published into chats.
- Define crash-safe tool deletion as a staged/idempotent operation: mark the runtime ID unavailable and prevent new runs; cancel/join active runs; atomically remove the definition, assistant assignments, and Variables from Settings; then clean only that tool's Secrets and private storage. The Settings removal is the durable recovery marker, and startup reconciliation retries any interrupted private-data cleanup. Preserve any already-committed conversation results.
- Make the delete confirmation explicit that the tool's private data/configuration will be removed but images and other media already delivered to conversations will remain.
- Never delete shared Secrets/Variables or files that have another explicit owner. With the current per-tool namespace, tool deletion removes only that tool's namespaced values.
- Keep immutable tool identity distinct from display name and import identity. Re-importing a tool must not silently regain access to deleted tool data or another tool's namespace. Explicit restore/import semantics must be defined.
- Storage disablement blocks future reads/writes but retains existing data until the user explicitly deletes it or deletes the tool; show that retained data exists and explain its ownership.
- Define behavior for tool deletion while a call is running and for storage writes racing with deletion. A late completion must not recreate deleted storage.
- Keep tool storage separate from the Secret store.
- Keep tool data in no-backup storage; never include it in Android Auto Backup, manual backups, database exports, or tool exports.
- Add tests for lazy creation, restart persistence, tool isolation, shared assistant access, quotas, path traversal, disablement, deletion, and import behavior.

Acceptance:
- A tool cannot access another tool's data or arbitrary app files.
- Its data survives app restart.
- No folder exists before the first successful write.
- Existing App Manager and managed-file behavior is unchanged.

## Stage 5 — Finish the Tool Builder and Tool Creator experience

**Goal:** make all implemented capabilities understandable and configurable in the existing Builder.

Tasks:
- Extend the existing full-page Builder; do not rebuild it.
- Add clear API access opt-in and concise explanation.
- Add named Secret/Variable declarations with required/optional status and descriptions.
- Add optional per-tool storage controls and clear retention explanation.
- Show missing configuration and capability requirements before testing/enabling.
- Include capability declarations in stale-test invalidation/fingerprinting.
- Update Simple and Advanced AI guidance to use the same reference syntax and explain setup names without asking for values.
- Keep AI assistance for every field in both modes.
- Update Review to summarize API access, required names, storage, approval, and test status.
- Update the built-in Tool Creator skill only to describe capabilities that are implemented and tested.
- Preserve the existing real QuickJS test, test-before-enable gate, stale-test invalidation, explicit save/enable/assignment, assistant-scoped selection, and one **Need approval** switch.
- Add focused model, validation, Builder-state, UI-state, and skill tests.

Acceptance:
- Simple and Advanced modes describe the same real capabilities.
- User remains in control of placing AI-generated content, testing, saving, enabling, assigning, and approval.
- The Builder and skill do not advertise unimplemented capabilities.
- All existing custom-tool behavior and approval flow remain intact.

## Stage 6 — Managed media results and end-to-end model handoff

**Goal:** let API-backed tools return useful media, not huge text blobs.

Tasks:
- Add typed image result handling for API binary/Base64 responses and safely retrievable URL responses: decode/download natively, validate format/size, materialize into conversation-owned managed storage, and return a typed image UIMessagePart.
- Preserve provider-specific vision behavior and verify the selected model's image modality before promising image understanding.
- Ensure the image is rendered in the tool result and can be passed to the model using the existing provider adapters.
- Verify managed local image paths in Markdown without breaking existing image/GIF rendering.
- Investigate audio/video/document parts only where both provider input serialization and UI rendering are genuinely supported. Do not advertise unsupported media types.
- Keep Base64 out of model context when typed media is supported.
- Add end-to-end tests for binary response -> managed file -> typed part -> renderer/provider handoff, plus invalid/oversized data.
- Persist chat-generated media under conversation/message ownership, never in tool-owned storage. The producing tool ID may be retained as non-owning provenance metadata, but must not control media lifetime.
- Treat temporary remote URLs and tool-private output files as inputs to materialization only; after publication the message points to a stable conversation-owned asset, not an expiring URL or tool directory.
- Ensure the message/media reference is committed atomically enough that the UI cannot show a durable message whose media was never safely stored. Clean temporary files on cancellation/failure.
- Verify media remains available after tool disable, tool deletion, tool edit, and tool re-import.
- Verify deleting one conversation removes only its media and references; other conversations remain intact. If deduplicated blobs are used, validate reference counting/ownership and final-owner garbage collection.
- Verify deleting a message, regenerating a response, editing/removing an image from a response, or clearing a conversation releases only media no longer referenced by surviving messages.
- Verify explicitly saved/exported user-owned copies survive conversation deletion, while temporary thumbnails and chat-only copies are removed.
- Test a tool deletion racing an active API/media call: committed results survive; uncommitted results are cancelled and cleaned; no late callback recreates tool-owned state or leaks orphaned files.

Acceptance:
- A tool can return a real image through the supported native path.
- The image appears in chat and is available to vision-capable model adapters.
- Existing image/GIF, ordinary Markdown, and provider behavior remain unchanged.

## Stage 7 — Custom-tool completion and regression gate

**Goal:** prove the complete custom-tool feature before beginning video work.

Tasks:
- Run all focused custom-tool, QuickJS, credential, storage, media ownership/lifecycle, settings normalization, backup/restore, allowed-host/SSRF, and skill tests.
- Verify chat backup/restore preserves media ownership and message references, and that deleting a tool does not alter chat backup data.
- Run lint, all unit tests, debug APK build, and the configured signed ARM64 release workflow.
- Inspect workflow logs and artifacts; verify APK package, ABI, signature, and version metadata where the workflow provides them.
- Review the full diff against the recorded base. Reject unrelated file changes.
- Confirm no secrets or ciphertext entered the repository or artifacts.
- Record exactly which checks passed and which remain unverified.

Acceptance:
- All relevant automated checks pass.
- API/network and storage are opt-in and enforced by runtime tests.
- Credentials are isolated and redacted.
- SSH and Custom JS search regression tests pass.
- The custom-tool implementation is stable enough to proceed to the independent video stage.
- No claim of device verification is made until the user actually tests it.

## Stage 8 — Implement inline Markdown video playback

**Start only after Stage 7 passes.**

**Goal:** render video exactly where it appears in the AI's Markdown, not as a card or separate attachment area.

Behavior:
- Inline, borderless, no extra card/frame/padding, natural aspect ratio, bounded by chat width.
- No autoplay.
- Local video shows its actual first frame without starting playback. Reuse the existing Media3 dependencies; do not add another playback stack unless the audit reveals a concrete blocker.
- Remote video URL is not fetched/downloaded merely to render or create a preview. Use a supplied poster if available; otherwise show a lightweight placeholder until explicit Play.
- Tap Play to load/start. While controls are visible, tap empty video area to hide controls without pausing. Tap again to reveal them.
- Controls fade after inactivity during playback; controls remain visible while paused.
- Overlay play/pause, seek/progress, elapsed/total time, ±10-second skip, and fullscreen.
- Handle buffering, load/play errors, lifecycle/backgrounding, orientation/fullscreen restoration, and accessibility.
- Preserve existing image/GIF/link Markdown behavior.
- Prefer the existing native media stack if present; select Media3/ExoPlayer only after dependency and lifecycle audit. Avoid WebView unless a concrete compatibility requirement justifies it.
- Offline caching is optional and must never consume storage silently.

Tests:
- Markdown placement and mixed text/video ordering.
- No autoplay.
- Local first-frame preview without playback.
- Remote no-fetch-before-Play behavior and supplied poster/placeholder.
- Tap-to-show/hide controls, auto-fade, paused persistence, seek, skip, fullscreen.
- Buffering, errors, lifecycle/backgrounding, accessibility.
- Image/GIF/links unchanged.

Acceptance:
- Video behaves as part of the Markdown message, not a card or separate attachment.
- Remote sources remain unloaded until explicit Play.
- Existing Markdown behavior remains intact.

## Stage 9 — Final integration, release verification, and handoff

Tasks:
- Run the full relevant test/lint/debug/release pipeline again after video integration.
- Re-run media ownership/deletion/backup tests for every newly supported media type; do not assume image coverage proves video/audio/document lifecycle correctness.
- Review all changed paths against the base and verify that only this plan's explicitly scoped custom-tool/media files changed.
- Inspect release artifact metadata/signature if produced.
- Update this new plan with commit SHAs, changed-file explanations, test/workflow links, and known limitations.
- Keep the old project plan untouched.
- Do not merge into protected branches or publish a release without explicit user permission.

Acceptance:
- Automated verification is complete and accurately recorded.
- Remaining manual/device checks are clearly separated from CI checks.
- Working branch is ready for user review; no protected branch was changed.

## Execution log

### Continuation branch established (2026-11-06)
- The original standalone plan was introduced on `feature/custom-tool-api-runtime`; its initial plan commit was `9e60c14a1d3fd4e2ef67e310c847622515ade148`.
- Before continuation, the source branch contained the media ownership/lifecycle contract and related deletion/cleanup requirements. Those inherited changes are preserved exactly; this continuation does not rewrite the source branch.
- Created `feature/custom-tool-api-runtime-next` from source commit `5178056124ba9728f9edb63f5d668bb52117f561`.
- GitHub comparison verified the new branch and `feature/custom-tool-api-runtime` were identical at creation (zero commits ahead/behind, no file differences).
- All subsequent edits, commits, tests, and implementation work must occur only on `feature/custom-tool-api-runtime-next`.
- Protected branches are not targets. No protected branch was modified by creating the continuation branch.
- The old `docs/LASTNAP_PROJECT_PLAN.md` remains outside this plan's editing scope.
- The prior architecture notes identify an existing QuickJSFetch bridge, SecretCipher, FilesManager primitives, and typed UIMessagePart media as possible foundations, not completed custom-tool capabilities.
- Source-code implementation has not yet begun. Stage 1 remains an audit/freeze-contract stage and must be completed before implementation edits.

# Changelog

## 3.16.5 - 2026/8/24
- **Behavior change:** a run where any test *errored* (rejected `openEyes`, render crash,
  Eyes construction failure) now exits **1** — previously such runs exited 0 and looked
  green to CI. Skip-only runs still exit 2; test outcomes (new test, mismatch) still exit 0
  without `-te`. Pipelines keying on exit codes may newly fail — those runs were already
  broken, just silently
- A baseline branch that doesn't exist on the server (`-bb`) now fails with a message naming
  the branch, instead of a raw `openEyes ... Bad Request(400)` dump — printed twice in folder
  runs and crashing compare mode with a stack trace
- Failures are reported once: the post-run abort no longer re-reports the same error, and
  every reported failure ends with the support contact (targeted hints, like the private-cloud
  or baseline-branch hint, add context instead of replacing it)
- The invalid-API-key message pointed at a dead docs URL (`Default.html#cshid=api`, baked into
  the Eyes SDK); it is rewritten to the live obtain-api-key page before printing
- `-pb` without `-br` fails at parse time with a clear message, instead of erroring inside a
  worker thread after "Starting tests"
- Image tests now report outcomes as result rows like PDF tests always did: a new test under
  `-pt` prints `[Unresolved], New test ...` with the dashboard URL instead of an exception
  dump, and a mismatch prints `[Unresolved]` instead of "detected differences!" noise
- GUI: run-level failures (nothing test-specific to attach them to) now show as a banner in
  the Tests pane pointing at the Log tab, and survive a page refresh; Parent branch without
  Branch is rejected when Run is clicked, before the run starts
- E2E suite: scenarios now assert the run produced actual test results, not just exit code 0
  (the `-bb` scenario had been green while every one of its runs silently failed)
- Fix: `-p url,user,password` treated the whole comma-joined value as the proxy URL, silently
  dropping the credentials — same commons-cli 1.6.0 regression that broke `-ac` (#49)
- Fix: `-rc`, `-mt`, and `-dn` consumed no value at all since the commons-cli 1.6.0 bump
  (2023-11): region capture, match timeout, and device name were silently ignored
- Fix: a failed batch-mapper run (`-mp`) now exits non-zero instead of printing a stack
  trace and exiting 0
- Fix: space-separated `-p url user password` silently dropped the credentials (commons-cli
  1.6.0 caps optional-arg options at one token); both the space- and comma-joined forms now
  work, and the same splitter covers `APPLITOOLS_PROXY`, so passwords containing commas no
  longer break it. Bare `-p` without a value is now a parse error instead of a silent no-op
- Stray command-line tokens (e.g. `-ari 1,1,1,1 2,2,2,2`, where the second region was
  silently dropped) now fail the run with a usage error pointing at the joined
  `x,y,w,h|x,y,w,h` format
- `.ps` and `.xps` files are now skipped with a clear reason instead of being uploaded as
  a text dump of their raw source: LibreOffice has no PostScript/XPS import filter and
  silently falls back to a plain-text import (a 3-page `.ps` produced a 529-page baseline).
  `.eps` still converts correctly via LibreOffice Draw
- Every CLI flag now has a parse-contract unit test (CliContractTest) and, where runnable, an
  end-to-end Eyes scenario, so a dependency update that breaks a flag turns CI red
- CI: new jar-smoke job runs the packaged jar on every push/PR (catches shading breakage);
  the Eyes E2E job now runs against the packaged jar and fails loudly when the API key is
  missing (Dependabot and fork runs, which never receive the secret, skip it instead)
- Improved: an invalid API key now prints the SDK message plus a hint to set the server URL when
  testing against a private cloud, instead of a raw `Unexpected error, EyesException` dump
- The default server URL (`https://eyes.applitools.com`) and how to change it are now called out
  everywhere: GUI Server URL placeholder + help text, `-s` CLI help, and a new
  "Setting the server URL" README section

## 3.16.4 - 2026/8/13
- Fix: `-ac` accessibility validation rejected every `Level:Guideline` value (e.g. `AA:WCAG_2_1`)
  with an "Unable to parse value" error — commons-cli 1.6.0 stopped splitting on `:` (#49)
- GUI: the Accessibility option is now a dropdown (Off, AA/AAA × WCAG 2.0/2.1) instead of a
  free-text field with an easy-to-mistype format
- Clearer `-ac` instructions in the README and CLI `--help`

## 3.16.3 - 2026/8/3
- Fix issue in which Mac environments were not able to click the folder popup

## 3.16.2 - 2026/7/31
- GUI: Cancel now takes effect within a page or two instead of waiting for the whole in-flight document; the cancelled test is never closed, so a cancelled comparison no longer leaves a half-created baseline behind
- GUI: the Tests list now shows the actual Eyes result status — Passed, Mismatch (Unresolved), Failed, New, Aborted, Error, Cancelled — instead of a ✓/✕ icon; New/Aborted/Cancelled count as neither passed nor failed in the summary
- GUI: Cancel now works during "Compare two documents" runs — it stops the comparison at the next safe point (in-flight uploads are allowed to settle) instead of silently doing nothing
- GUI: clicking Cancel keeps the live run on screen with a "Cancelling…" state until the backend confirms, instead of pretending the run stopped — this removes the "409: A run is already in progress" loop on the next Run click
- GUI: tests that never completed (e.g. a cancelled comparison) are now shown as cancelled (⊘) instead of spinning forever
- GUI: if Run is rejected because a run is already in progress, the Status pane now re-syncs to show that in-flight run

## 3.16.0 - 2026/7/21
- New: Compare two documents directly against each other, without scanning a folder — `-doc1`/`-doc2` on the CLI, or the "Compare two documents" toggle in the GUI
- GUI: automatic update check with a one-click installer download from the app
- GUI: Windows installer now shows a destination-folder chooser instead of installing silently to a fixed path
- GUI: design refresh — elevation/shadows on cards, consistent focus rings, spacing, and required-field markers on the Setup card

## 3.15.0 - 2026/7/17
- Releases are now published as full releases marked Latest on GitHub (previously pre-releases)

## 3.14.0 - 2026/7/17
- GUI: status rows now show a thumbnail preview of the image or PDF page being tested
- GUI: replaced the default Java icon with the Applitools logo for installed apps and `--gui` runs

# 3.8.0 - 2026/2/18
- Add support for IgnoreColors match level 

## 3.6.3 - 2024/7/25
- Fix issue in which using `-te` prevents Java process from exiting gracefully

## 3.5.4 - 2023/11/30
- Resolve issue relating to universal executables not being available for all OSs

## 3.5.3 - 2023/11/16
- Add baselineBranchName argument (-bb)

## 3.5.2 - 2023/7/24
- Bump version of eyes-images, opencsv, and pdfbox

## 3.5.1 - 2023/5/1 
- Bump eyes-images version to 5.49.0

## 3.5.0 - 2023/4/20
- Added regex filtering for files. 
- Passing `-rf "Lorem.*"` instructs ImageTester to only test files with names that start with "Lorem"

## 3.4.0 - 2023/4/7
- Added option to define layout regions, content regions, and ignore regions for individual tests using the batch mapper. 
  
    **Important:** Existing batch mapper CSVs must be updated to include these new parameters. 
  

## 3.3.2 - 2023/3/14
- Add support for jbig images

## 3.0.4 - 2022/9/27
- Refactor string parsing to be slightly more forgiving (whitespace is removed before parsing arguments like page numbers).

## 3.0.3 - 2022/9/2
- Add -te flag, which instructs ImageTester to throw an exception on mismatches or failed tests.

## 3.0.2 - 2022/8/27
- Slight optimizations. No change in function. 

## 3.0.1 - 2022/8/1
- Restored EyesUtilities functionality with updated dependencies

## 3.0.0 - 2022/8/1
- Removed dependencies that were presenting vulnerabilities
- Removed EyesUtilities, so that EyesUtilities can be re-architected
- This version is a regression: PostScript support is removed, and EyesUtilities commands are removed

## 2.3.2 - 2022/3/21
- Added feature that allows users to add ignored regions, content regions, and layout regions
- "-ir" allows for ignore regions, "-cr" allows for content regions, "-lr" allows for layout regions

## 2.3.1 - 2022/2/18
- Changed BatchMapper metadata [filePath, testName, app, os, browser, viewport, viewport, matchsize, pages, matchLevel]
- Added some extra checks for the BatchMapper CSV configuration

## 2.3.0 - 2022/2/14
- Added support for the BatchMapper feature in the ImageTester, which provides an alternative execution method for ImageTester tests runs. Readme updated with instructions
- Added dependencies: [jackson-core@2.13.1, opencsv@5.3, jackson-dataformat-csv@2.13.1]

## 2.2.1 - 2022/2/16
- Revise logic for EyesUtilities, so that it's always available with valid enterprise view key.

## 2.2.0 - 2021/10/15
- Added -dcb flag which will stop batches from closing automatically 

## 2.1.3 - 2021/9/30
- Use Scalr library for more accurate resizing to resolve bug with certain viewport sizes. 
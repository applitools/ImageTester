# Changelog

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
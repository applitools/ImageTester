# Image Tester [ ![Download](https://img.shields.io/github/v/tag/applitools/ImageTester?label=Download&style=plastic) ](https://github.com/applitools/ImageTester/releases/latest)

ImageTester is a tool to perform visual tests on images, PDF files, Office files, and more!
Point it at a single file or an entire folder tree and it uploads every image and document page
to Applitools Eyes for baseline comparison and diff detection.

If you don't have your Applitools account yet, 
please [sign up first](https://applitools.com/sign-up/) 
and get your Applitools api-key that will be used next to execute the tests.

## ImageTester GUI

<img width="1153" height="996" alt="image" src="https://github.com/user-attachments/assets/ed12b67d-1c54-4824-b082-752fffca6a2c" />

The ImageTester GUI is the recommended way to use ImageTester — a full desktop application that
puts everything ImageTester can do behind a point-and-click interface. No Java to install, no
terminal, no setup.

Download the installer for your OS from the
[latest release](https://github.com/applitools/ImageTester/releases/latest) — Windows (`.msi`),
macOS (`.dmg`, Apple Silicon and Intel), or Linux (`.deb`). Each installer bundles everything the
app needs; double-click and the app opens in your browser.

Highlights:

+ **Run tests in a few clicks** — pick a file or folder, choose a match level, hit Run.
+ **Compare two documents** — a toggle on the Setup card for diffing exactly two files directly
  against each other; see [Comparing Two Documents Directly](#comparing-two-documents-directly).
+ **Every option, organized** — all of the options documented below are available in the GUI,
  grouped into tabs that mirror the reference sections in this README; each tab links back to
  the matching section.
+ **Live progress** — status rows update as tests run, with a thumbnail preview of the image or
  PDF page being tested.
+ **Stays up to date** — the app checks for new releases on launch and can download, verify, and
  start the installer for you with one click.

Installers are not yet code-signed, so your OS will ask you to confirm the first launch — the
release notes walk you through it.

Already have the JAR? The same app is built in — launch it with:

>java -jar ImageTester.jar --gui

## How Tests Are Organized

Whether you run from the GUI or the command line, ImageTester can be invoked on a single file or a complex folder structure with mixed content.
Once provided a complex folder structure the tool recursively scans the structure and determines on each level what should be the batch-name, 
the directoryTest-name and the tag values relatively to the target files.  
For example, given the following folder structure:
```
+- Folder A  
|  +- Folder B
|  |  +- Screenshot1.png
|  |  +- Screenshot2.png
|  +- Folder C
|  |  +- Screenshot3.png
|  |  +- Screenshot4.png
|  |  +- Screenshot5.png
```
The following tests will be generated in Applitools:
```
Batch name - A:
    Test name - B > steps: Screenshot1, Screenshot2
    Test name - C > steps: Screenshot3, Screenshot4, Screenshot5
```

The parameters for Applitools directoryTest will be derived from the file and the folder structure according to the following table:

|            | Single image file                           | Multi-image file (PDF)            | 
|------------|---------------------------------------------|-----------------------------------|
| Step tag   | The filename                                | Step index                        |
| Test name  | Parent folder-name if applicable*           | The filename                      |
| Batch name | 2nd level parent folfer-name if applicable* | Parent folder-name if applicable* |

\* When no sufficient levels in the specified structure to derive all directoryTest parameters, the values will be taken from the child value.
For example, for the following structure:
```
+- Folder A
|  +- Screenshot1.png
|  +- Screenshot2.png

```
The following directoryTest will be generated in Applitools:
```
Batch name - A:
    Test name - A > steps: Screenshot1, Screenshot2
```
Note that the batch name was derived from the directoryTest name as there is no additional 
folder level that can be used as batch name.

## Command Line
Prefer a terminal, or running in CI? Everything the GUI does is also available from the command
line. The tool is built in Java and requires a minimal set of parameters. The minimal command looks as follows:

>java -jar ImageTester.jar -k [api-key]

\* With the minimal set of parameters the search folder is assumed to be the execution folder.

+ Required parameters:
    + `-k [api-key]` - Applitools api key, can also be set by the environment variable APPLITOOLS_API_KEY
    + `-f [path]` - A path to the target folder or file (default: the execution folder)

The optional parameters and flags below are grouped by category. These categories mirror the tabs in the ImageTester GUI, so a link from the GUI lands on the matching section here.

### Metadata options
Labels and identifiers attached to your tests — shown on the Applitools dashboard.

+ `-a [app-name]` - Set the application name shown on the dashboard; default = ImageTester
+ `-os [osname]` - Set the operating-system label recorded for the test
+ `-ap [browser name]` - Set the browser or hosting-application label recorded for the test
+ `-en [environment]` - Set the environment name identifier for the test
+ `-dn [device name]` - Set the device-name metadata shown on the dashboard
+ `-vs [WidthxHeight]` - Set the viewport size identifier, e.g. `1000x600`
+ `-pr [key1:value1|key2:value2]` - Add custom key/value properties to each test.
  **Example:** `-pr "prop1:value1|prop2:value2|prop3:value3"` adds:
    + prop1, value1
    + prop2, value2
    + prop3, value3

### Execution options
How the run executes and what it writes to the log.

+ `-th [number]` - Maximum concurrent workers (threads); default = 3
+ `-rt [number]` - Parallel page-render threads for multi-page PDF tests; default = min(4, CPU cores - 1). Pages are rendered in the background while earlier pages upload, keeping a single test with ordered steps. Set `-rt 1` to disable
+ `-debug` - Turn on verbose debug prints
+ `-log` - Turn on Applitools SDK log prints
+ `-rf [regex]` - Only test files whose name matches the regular expression.
  **Example:** `-rf "Lorem.*"` only tests files that start with "Lorem" (e.g. Lorem1.pdf, Lorem2.pdf)
+ `-lf [log-file]` - DEPRECATED. Please set the log path with the environment variable *APPLITOOLS_LOG_DIR* instead. <br>
  + **The logs are automatically created and saved to:**
  + Mac/Linux - `$TMPDIR/applitools-logs/`
  + Windows (PowerShell) - `$env:TEMP/applitools-logs/`

### Matching options
How images are compared and which differences count as failures.

+ `-ml [match-level]` - Set the comparison level, one of Strict/IgnoreColors/Layout/Exact/None; default = Strict
+ `-ms [{width x height}]` - Match the size of images to a specific width and/or height, e.g. `1000x` (by width), `x600` (by height), `1000x600` (exact — may lose proportions)
+ `-mt [ms]` - Set match timeout and retry timeout in milliseconds (minimum 500, default = 500)
+ `-ic [{header,footer,left,right}]` - Cut pixels from each side before comparing; missing notations allowed, e.g. `-ic ,,10,4`
+ `-rc [x,y,width,height]` - Capture (test) only a particular region of PDFs/Images.
  **Example:** `-rc "200,500,1000,1000"` tests only the region at x:200, y:500, width:1000, height:1000
+ `-ac [Level:GuidelineVer]` - Set accessibility validation, optionally with arguments split by `:`; default "AA:WCAG_2_0", available [AA|AAA:WCAG_2_0|WCAG_2_1]
+ `-id` - Ignore displacement of shifting elements
+ `-as` - Automatically save baselines on failures
+ `-pt` - Prompt for new tests — new tests are not saved automatically; review and save them manually
+ `-te` - Throw an exception if Eyes detects a mismatch or failure

### Batch and branch options
Organize results into batches and branches on the dashboard.

+ `-br [branch]` - Set the branch
+ `-pb [parent-branch]` - Set the parent branch
+ `-bb [baseline-branch]` - Set the baseline branch name
+ `-bn [baseline]` - Set a custom baseline name
+ `-fb [batchName]` - Aggregate all discovered tests into a single flat batch.
    + To also set a batch id: `-fb BATCH_NAME<>BATCH_ID`
+ `-sq [sequenceName]` - Set the batch sequence name for unified insights on the dashboard
+ `-fn [testName]` - Force all test names to a specific name (all files match a single baseline)
+ `-nc` - Send a batch notification on completion
+ `-dcb` - Do not automatically close the batch(es) when the run completes
+ `-mp [path]` - Run tests from a Batch Mapper CSV instead of a path (see [Using The Batch Mapper](#using-the-batch-mapper))

### Region options
Mark areas to ignore, treat as content, or check for layout/accessibility only. Coordinates are `x,y,width,height`, multiple separated by `|`.

+ `-ir` - Ignore regions, applied to all pages.
  **Example:** `-ir "300,300,500,100|500,500,200,200"` creates ignored regions at:
    + x:300, y:300, width:500, height:100 and
    + x:500, y:500, width:200, height:200
+ `-cr` - Content regions, applied to all pages (same coordinate format)
+ `-lr` - Layout regions, applied to all pages (same coordinate format)
+ `-ari` - Accessibility ignore regions (same coordinate format)
+ `-arr` - Accessibility regular-text regions; without coordinates the region is the full viewport
+ `-arl` - Accessibility large-text regions; without coordinates the region is the full viewport
+ `-arb` - Accessibility bold-text regions; without coordinates the region is the full viewport
+ `-arg` - Accessibility graphic regions; without coordinates the region is the full viewport

### Connection options
Server, proxy, and SSL settings.

+ `-s [server]` - Set the Applitools server URL, can also be set by the environment variable APPLITOOLS_SERVER_URL
+ `-p [http://proxy{,user,pass}]` - Set the proxy with optional username and password, can also be set by APPLITOOLS_PROXY
+ `-dv` - Disable SSL certificate validation. !!!Unsecured!!!

### Using The Batch Mapper
The Batch Mapper is a feature that allows you to specify tests from a CSV instead of supplying a path or file in the traditional way.
<br><br>
A common use-case is the desire to run tests on a PDF that has changed it's composition.<br>
Consider a PDF that initially contains 4 pages (Page 1, Page 2, Page 3, Page 4).<br>
Now imagine that this PDF changes over time, and 2 new pages are added to the beginning of the PDF.<br>
It can be difficult to test these pages given the current composition of the ImageTester. 
<br><br>
For this use case, it can be beneficial to use the Batch Mapper feature. 
<br><br>
To use it, supply a path to a batch mapper configuration file (a '|' delimited CSV).
<br><br>
#### Batch Mapper parameters
- `filePath`- The location of files (PDF/Image) being tested, can also be a folder
- `testName` - The name of the test as it will appear on the Applitools dashboard (Optional)
- `app` - The app name of the test as it will appear on the Applitools dashboard (Optional)
- `os` - The operating system to be tested on (Optional)
- `browser` - The browser to be tested on (Optional)
- `viewport` - The viewport for the test `-vs` flag (Optional)
- `matchsize` - The size to adjust the image , `-ms` flag (Optional)
- `pages`- Pages to be tested for this PDF. (ie: 1,2,5,7,10-15) (Optional)
- `matchLevel`- The MatchLevel for the test (Optional)
#### Additional Notes
To have each of your tests appear in the same batch, set the `APPLITOOLS_BATCH_ID` environment variable in your CLI environment. 

#### Sample CSV:
```
filePath|testName|app|os|browser |viewport|matchsize|pages|matchLevel|layoutRegions|contentRegions|ignoreRegions|
docs/a.pdf|Test1|AppA|Linux|Chrome|1024x748||1|Strict||800,800,100,100|300,300,300,100|
docs/a.pdf|Test2|AppA||||x748|1-3|Layout|||500,500,500,100|
docs/b.pdf|Test3|AppB|||||2-5|200,200,500,500|||
```

### Comparing Two Documents Directly
Sometimes you just want to diff two specific documents against each other — no folder scanning,
no existing baseline lookup on the dashboard.

In the GUI, this is the "Compare two documents" toggle on the Setup card: it swaps the single
Source picker for Doc 1 / Doc 2 file pickers and a required Comparison name field (the same
`-fn` value described below).

On the command line:

+ `-doc1 [path]` - First document to compare (image or PDF)
+ `-doc2 [path]` - Second document to compare (image or PDF)

Both flags are required together, and are mutually exclusive with `-f`. You must also set
`-fn [name]` (Forced name) — Doc 1 and Doc 2 need to share this name so Eyes treats them as the
same test: Doc 1's run establishes the baseline, then Doc 2's run is compared against it.

```
java -jar ImageTester.jar -k [api-key] -doc1 v2.pdf -doc2 v3.pdf -fn "contract-v2-vs-v3"
```

**Use a unique `-fn` value for each comparison.** This reuses the tool's normal Eyes baseline
behavior — the first run under a name sets the baseline, later runs under the same name diff
against whatever's already there. Reusing a name from an earlier comparison means whichever
document ran first *that time* becomes the baseline, not necessarily today's Doc 1.

For multi-page PDFs, use `-sp` (see [PDF and document options](#pdf-and-document-options)) to pick
which pages to compare — the same range applies to both documents, so both must actually have
that many pages.

Before running, ImageTester pre-checks Doc 1 and Doc 2 and warns you when page dimensions or page
counts don't match — since Eyes resolves baselines by rendered viewport, a mismatch would silently
produce new baselines instead of a comparison. The warning suggests remedies like `-sp`, `-ms`,
`-vs`, or `-tp`. The run is blocked outright for a corrupt document, a password-protected PDF
without `-pp`, or a PDF with no pages.

### PDF and document options
Options that apply when testing PDFs and other documents.

+ `-di [dpi]` - Rendering quality (dots per inch) for PDF pages; default = 250
+ `-sp [pages]` - Comma-separated page numbers/ranges to include (e.g. 1,2,5,7,10-15); default = all pages
+ `-tp [size]` - Trim print margins (crop marks, slug area) from PDF pages before comparing. Bare `-tp` (or `-tp auto`) detects the trim area from TrimBox metadata or crop marks; `-tp 603x774` crops a centered box of that size in PDF points. Enabling trim changes checkpoint dimensions, so existing baselines will mismatch on the first trimmed run. Marks flattened into raster scans are not detected — use the explicit size for those.
+ `-pp [password]` - Password for opening protected PDF files
+ `-pn` - Preserve the original test names when testing only selected pages
+ `-st` - Split a multi-page document into individual single-step tests
+ `-nf` - Normalize all PDF fonts to Helvetica 12pt before rendering. See [Font Normalization](#font-normalization).
+ `-lo` - Use legacy (pre-2.0) file ordering to stay compatible with older baselines
+ `-rwauto` - Auto-detect and remove a watermark (a stamped outline like a diagonal "UAT - Proof"). See [Watermark Removal](#watermark-removal).
+ `-rwo [dir]` - Standalone output mode: write cleaned PDFs to the directory and exit without uploading. Combine with `-rwauto`. See [Watermark Removal](#watermark-removal).

### Font Normalization
Font changes (family swaps, weight tweaks, kerning differences) are one of the most common sources of
false-positive diffs when comparing PDFs across document generation pipelines. The `-nf` / `--normalizeFont` flag provides
a middle ground: before a PDF page is rasterized, each text run is decoded to Unicode through its original
font's encoding, then re-encoded for **Helvetica 12pt**, producing a deterministic render that is insensitive
to typographic changes while still catching structural differences (missing text, reordered content, layout
regressions).

**Usage:**
```
java -jar ImageTester.jar -k [api-key] -f report.pdf -nf
```

**Behavior:**
- The original PDF document on disk is **never modified**. Normalization operates on a copy of the page's
  content stream and resources; the baseline file and any downstream consumers are untouched.
- Image content, vector graphics, and page geometry (media box, matrix) are preserved as-is.
- Runs whose font cannot be decoded to Unicode keep their original font, size, and bytes — `-nf` never guesses.
- Characters Helvetica cannot represent (for example non-Latin scripts) are rendered as `?`, and each
  substitution is logged with a warning naming the code point.
- Because normalized output changed in 3.17, enabling `-nf` after upgrading invalidates `-nf` baselines
  created by earlier versions for documents with embedded or subset fonts — plan a baseline refresh.

**When to use it:**
- Baselines are breaking because the document generator upgraded a font library or switched a typeface.
- You want to validate structural/layout correctness of a document independently of its typography.
- You are comparing output from two rendering pipelines that embed fonts differently.

**When *not* to use it:**
- Typography is part of what you are validating (brand compliance, marketing collateral).
- You need to verify that a specific font is present and rendered correctly.

**Note:** Enabling `-nf` invalidates existing baselines — normalized renders will not match a baseline
captured without normalization. Plan for a baseline refresh when rolling this out.

### Watermark Removal

Pre-production watermarks ("DRAFT", "PRE-Proof", etc.) make every page diff against a
clean baseline. The `-rwauto` flag strips them from PDFs in memory before uploading to Eyes.
**The original PDFs on disk are never modified.**

ImageTester groups your PDFs by their containing folder, detects the watermark's fill color shared
across each group, then removes only the paths drawn in that color — everything else is untouched.
It works whether the watermark is stamped identically in every PDF or restamped at a different
position in each one.

Requirements:
- **At least 2 PDFs per folder, all from the same source** (same template, all carrying the same
  watermark). Detection works by comparing the PDFs against each other, so a folder must not mix
  unrelated documents or include a PDF that has no watermark.
- Subfolders per environment (`pre/`, `uat/`, `staging/`)? Point at the parent — each subfolder is
  detected and cleaned independently.

```
java -jar ImageTester.jar -k YOUR_API_KEY -f pdfs/ -rwauto -a YourApp -fb YourBatch
```

On success ImageTester logs the color it found, e.g.
`[uat] Watermark color rgb(179, 179, 179) detected across 4 PDF(s)`.

#### Preview locally before uploading

Add `-rwo <dir>` to write the cleaned **PDFs** to a folder and exit without contacting
Eyes:

```
java -jar ImageTester.jar -f pdfs/ -rwauto -rwo cleaned/
```

Open the cleaned PDFs to confirm the watermark is gone and nothing else changed, then re-run without
`-rwo` to upload.

#### Troubleshooting

- **"you're testing one PDF on its own" / nothing removed (`-rwauto`)** — auto mode needs at least 2
  same-source PDFs in the folder. Add another report/invoice/email from the same system and re-run.
- **Watermark still visible after `-rwauto`** — the folder probably mixes documents from different
  sources, or includes a PDF that has no watermark. Make sure each folder holds only same-source PDFs
  that all carry the watermark. If it still doesn't work, contact Applitools support
  (support@applitools.com) with a sample PDF.

**Note:** cleaning changes what Eyes sees, so it invalidates baselines captured before cleaning was
enabled. Plan a baseline refresh on rollout.

### Download options
Enterprise features in combination with [Eyes Utilities](https://github.com/applitools/EyesUtilities).
Several EyesUtilities functions are integrated into ImageTester. Downloading results requires an
enterprise view-key.

>java -jar ImageTester.jar -k [api-key] -vk [view-key] [options]

+ Required parameters:
    + `-k [api-key]` - Applitools api key
    + `-vk [view-key]` - Applitools enterprise view-key
+ Selective flags - required one or more:
    + `-gd` - Get diff images of the failed steps
    + `-gi` - Get baseline and actual images of the failed steps
    + `-gg` - Get animated gifs of the failed steps
+ Optional parameters and flags:
    + `-of [path]` - Specify a custom output path or path-template

## CI/CD integration
Once setting the required environment variables, the ImageTester is able to pick them up and use to sync with the results
with other tools that out there for CI/CD integration.

The parameters:
+ `JOB_NAME` - The name of the job as it was set in CI/CD
+ `APPLITOOLS_BATCH_ID` - Job id, a unique identifier of the current job.

## Automatic Update Check
Both the GUI and the CLI check GitHub's releases once per launch and let you know when a newer
version is available — the GUI shows a banner with a one-click install button (downloaded to
your Downloads folder and verified against the release's checksum) when running as an installed
app, and the CLI prints a one-line note to stderr.

Set `IMAGETESTER_SKIP_UPDATE_CHECK=1` to disable it entirely — useful for CI runs or air-gapped
environments where the outbound network call to GitHub isn't wanted.

## Developing

Working on ImageTester itself? [DEVELOPING.md](DEVELOPING.md) covers building, running
the GUI locally, tests, architecture, releasing, and CI debugging.
[CONTRIBUTING.md](CONTRIBUTING.md) covers the data-handling rules (this repo handles
customer files during support work).

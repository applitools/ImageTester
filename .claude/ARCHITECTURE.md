# ImageTester Architecture Reference

**Version:** 3.8.0 | **Java:** 1.8 | **Build:** Maven (uber JAR via shade plugin)

## What It Does

CLI tool that performs **visual regression testing** on images and PDFs using the Applitools Eyes SDK. Users point it at files/folders, and it uploads screenshots to Applitools for baseline comparison and diff detection.

```
java -jar ImageTester.jar -k <api-key> -f <path> [options]
```

## Execution Flow

```
main() → parse CLI args → Config + EyesFactory
    ↓
Suite.create(root, config, executor)
    ↓ recursive file/folder discovery
    ├─ Image file → ImageFileTest (single-step)
    ├─ PDF file → PdfFileTest (multi-step, one step per page)
    ├─ PDF + splitSteps → PDFFileBatch (each page = separate test)
    ├─ PDF + batchMapper → BatchMappedPdfFileTest
    └─ Folder → FolderTest (all images = steps in one test)
    ↓
suite.run() → enqueues tests to TestExecutor
    ↓
TestExecutor (fixed thread pool, ThreadLocal<Eyes>)
    ↓ per test:
    eyes.open() → eyes.check() per step → eyes.close()
    ↓
Results logged, batches closed
```

## Package Layout

```
com.applitools.imagetester/
├── ImageTester.java          # Entry point, CLI parsing (Apache Commons CLI)
├── Suite.java                # Recursive file discovery, test/batch creation
├── TestObjects/
│   ├── ITest.java            # Interface: run, name, viewport, isEmpty
│   ├── IDisposable.java      # Interface: dispose() for resource cleanup
│   ├── TestBase.java         # Abstract: file, config, image loading/resizing, runSafe
│   ├── DocumentTestBase.java # Abstract: extends TestBase, adds page list parsing
│   ├── ImageFileTest.java    # Single image test (TIFF→PNG conversion)
│   ├── PdfFileTest.java      # PDF as multi-step test (PDFBox rendering)
│   ├── FolderTest.java       # Folder of images as multi-step test
│   ├── BatchMappedPdfFileTest.java  # PDF test driven by CSV mapping
│   └── ImagesCheckSettingsFactory.java  # Builds Eyes CheckSettings (regions, accessibility)
├── BatchObjects/
│   ├── IBatch.java           # Interface: isEmpty, run(executor)
│   ├── BatchBase.java        # Abstract: holds tests list + BatchInfo, runs them
│   ├── Batch.java            # Simple batch (folder name)
│   └── PDFFileBatch.java     # Split-mode: each PDF page = separate test, shared PDDocument
├── BatchMapper/
│   ├── BatchMapDeserializer.java  # Jackson CSV reader (pipe-delimited)
│   └── BatchMapPojo.java         # CSV row model
├── Constants/
│   └── ApplitoolsConstants.java   # Env var names
└── lib/
    ├── Config.java            # All configuration state (public fields), region parsing
    ├── TestExecutor.java      # Thread pool, batch assignment, result collection
    ├── EyesFactory.java       # Builder pattern → Eyes instance per thread
    ├── EyesUtilitiesConfig.java  # Enterprise diff/image/GIF download settings
    ├── Logger.java            # Console output, debug mode, result/exception reporting
    ├── Utils.java             # Enum parsing, page notation, SSL bypass, result downloads
    ├── Patterns.java          # Regex: IMAGE (jpg|png|gif|bmp|tif) and PDF
    └── ExecutorResult.java    # TestResults + runtime duration wrapper
```

## Class Hierarchy

```
ITest
  └─ TestBase (abstract)
       ├─ ImageFileTest
       ├─ DocumentTestBase (abstract)
       │    ├─ PdfFileTest
       │    └─ BatchMappedPdfFileTest
       ├─ FolderTest
       └─ PDFFileBatch.PDFPageTest (inner class, also implements IDisposable)

IBatch
  └─ BatchBase (abstract)
       ├─ Batch
       └─ PDFFileBatch
```

## Key Design Decisions

### Threading Model
- `TestExecutor` wraps a `FixedThreadPool` with `ThreadLocal<Eyes>` — one Eyes SDK instance per thread, reused across tests.
- PDFFileBatch uses `synchronized` on the parent object to serialize PDFBox rendering (PDFRenderer is not thread-safe).

### Test Discovery (Suite constructor)
- Recursive: each directory creates a `Batch`, recurses into children.
- Single image file → forces `splitSteps=true` (one test per file).
- PDF in split mode → `PDFFileBatch` (each page is an independent test).
- PDF in normal mode → `PdfFileTest` (all pages are steps in one test).
- Folder → `FolderTest` (all images are steps in one test).
- Files sorted alphabetically unless `-lo` legacy mode.

### Batch Hierarchy
- Default: folder name → batch name, child test names → file/subfolder names.
- Flat batch (`-fb`): all tests go into a single batch.
- Sequence name (`-sq`): groups batches in Applitools dashboard insights.

### Image Processing
- TIFF files auto-converted to PNG via `ImageFileTest.convertTiffToPng()`.
- Resize via imgscalr (`-ms` flag) — proportional by width, height, or exact.
- Region capture (`-rc`) crops to specified rectangle before comparison.
- Image cut (`-ic`) via Eyes `UnscaledFixedCutProvider` — removes header/footer/margins.

### PDF Processing
- PDFBox `PDFRenderer.renderImageWithDPI()` converts pages to `BufferedImage`.
- Default DPI: 250. Configurable via `-di`.
- JBIG2 codec registered manually for legacy PDF support.
- Password-protected PDFs via `-pp`.
- Page selection: `-sp 1,3,5-10` parsed by `Utils.parsePagesNotation()`.

### BatchMapper Mode (`-mp`)
- Completely separate execution path in `ImageTester.runTestWithBatchMapper()`.
- CSV (pipe-delimited) defines: filePath, testName, app, os, browser, viewport, matchsize, pages, matchLevel, regions.
- Each CSV row becomes an independent test run with its own Config + EyesFactory.
- Parallel execution via Java `.parallelStream()`.

## Configuration Flow

```
CLI args → Config (mutable POJO with public fields)
         → EyesFactory (builder pattern, immutable after build)
         → TestExecutor (thread pool + factory)
         → Suite (recursive discovery, test creation)
```

Config is passed by reference to all test objects. `EyesFactory.build()` creates Eyes instances per-thread.

## Dependencies

| Library | Purpose |
|---------|---------|
| eyes-images-java5 (5.81.10) | Applitools Eyes Images SDK |
| eyes-sdk-core-java5 (5.86.11) | Core SDK |
| eyes-universal-core-* (5.77.10) | Platform-specific binaries (Win/Mac/Linux/Alpine/ARM) |
| EyesUtilities (1.6.2) | Enterprise: download diffs, images, animated GIFs |
| pdfbox (2.0.29) | PDF rendering |
| jbig2-imageio (3.0.4) | JBIG2 image codec for PDFs |
| imageio-jpeg/tiff (3.12.0) | TwelveMonkeys: JPEG/TIFF support |
| commons-cli (1.6.0) | CLI argument parsing |
| commons-io (2.11.0) | File utilities, name comparator |
| commons-lang3 | StringUtils |
| jackson-core (2.13.4) | JSON |
| jackson-dataformat-csv (2.13.4) | CSV parsing for BatchMapper |
| opencsv (5.8) | CSV reading |
| imgscalr | Image scaling |
| slf4j-simple (1.7.32) | Logging |

## Environment Variables

| Variable | Purpose |
|----------|---------|
| `APPLITOOLS_API_KEY` | API key (fallback for `-k`) |
| `APPLITOOLS_SERVER_URL` | Server URL (fallback for `-s`) |
| `APPLITOOLS_PROXY` | Proxy: `url,user,pass` (fallback for `-p`) |
| `APPLITOOLS_BATCH_ID` | Batch ID override |
| `JOB_NAME` | CI/CD job name → batch name |
| `APPLITOOLS_VIEW_KEY` | Enterprise view key (fallback for `-vk`) |
| `APPLITOOLS_LOG_DIR` | Log directory path |

## Known Code Quirks

1. **Config uses public fields** — not a typical Java bean, no encapsulation.
2. **`-dn` vs `-de`** — two different option definitions both map to "deviceName".
3. **`PdfFileTest.run` calls `eyes.close(false)`** while `ImageFileTest.run` calls `eyes.close(true)` — inconsistent throw-on-failure behavior.
4. **Tests disabled** — `maven-surefire-plugin` has `skipTests: true`.

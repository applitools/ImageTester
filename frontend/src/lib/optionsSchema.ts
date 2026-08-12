export type ControlType =
  | "text"
  | "password"
  | "number"
  | "checkbox"
  | "select"
  | "regions"
  | "proxy"
  | "properties"
  | "imagecut"
  | "watermarkout";

export type TabId =
  | "metadata"
  | "execution"
  | "matching"
  | "batch"
  | "regions"
  | "pdf"
  | "connection"
  | "watermark"
  | "downloads";

export interface OptionSpec {
  flag: string;
  label: string;
  type: ControlType;
  tab: TabId;
  help?: string;
  options?: string[];
  default: unknown;
}

const README_BASE = "https://github.com/applitools/ImageTester";

// GitHub auto-generates these anchors from the README's section headings.
// Each tab maps to the categorized section that documents its options.
const TAB_ANCHORS: Record<TabId, string> = {
  metadata: "#metadata-options",
  execution: "#execution-options",
  matching: "#matching-options",
  batch: "#batch-and-branch-options",
  regions: "#region-options",
  connection: "#connection-options",
  pdf: "#pdf-and-document-options",
  watermark: "#watermark-removal",
  downloads: "#download-options",
};

/** README section that explains a given option, used to deep-link each help tip. */
export function docUrl(spec: OptionSpec): string {
  if (spec.flag === "nf" || spec.flag === "nfj") return README_BASE + "#font-normalization";
  return README_BASE + TAB_ANCHORS[spec.tab];
}

export const TABS: { id: TabId; label: string; description: string }[] = [
  { id: "metadata", label: "Metadata", description: "Labels and identifiers attached to your tests — shown on the Applitools dashboard." },
  { id: "execution", label: "Execution", description: "How the run executes and what it writes to the log." },
  { id: "matching", label: "Matching", description: "How images are compared and which differences count as failures." },
  { id: "batch", label: "Batch & Branch", description: "Organize results into batches and branches on the dashboard." },
  { id: "regions", label: "Regions", description: "Mark areas to ignore, treat as content, or check for layout/accessibility only. Coordinates are x, y, width, height." },
  { id: "pdf", label: "PDF & Documents", description: "Options that apply when testing PDFs and other documents." },
  { id: "connection", label: "Connection", description: "Server, proxy, and SSL settings." },
  { id: "watermark", label: "Watermark", description: "Strip draft/proof watermarks from PDFs before comparing them." },
  { id: "downloads", label: "Downloads", description: "Enterprise: pull diff images, screenshots, and GIFs after the run. Requires a view-key." },
];

export const OPTION_SPECS: OptionSpec[] = [
  // Metadata
  { flag: "a",  label: "App name",      type: "text",       tab: "metadata", help: "Application name shown on the dashboard. Default: ImageTester.", default: "" },
  { flag: "os", label: "Host OS",       type: "text",       tab: "metadata", help: "Operating-system label recorded for the test (metadata only).", default: "" },
  { flag: "ap", label: "Host app",      type: "text",       tab: "metadata", help: "Browser or hosting-application label recorded for the test (metadata only).", default: "" },
  { flag: "en", label: "Environment",   type: "text",       tab: "metadata", help: "Environment name identifier recorded for the test.", default: "" },
  { flag: "dn", label: "Device name",   type: "text",       tab: "metadata", help: "Device name shown in the dashboard (metadata only).", default: "" },
  { flag: "vs", label: "Viewport size", type: "text",       tab: "metadata", help: "Viewport size identifier as WidthxHeight, e.g. 1000x600.", default: "" },
  { flag: "pr", label: "Properties",    type: "properties", tab: "metadata", help: "Custom key/value properties attached to each test (searchable on the dashboard).", default: "" },
  // Execution
  { flag: "th",    label: "Threads",      type: "number",   tab: "execution", help: "Maximum concurrent worker threads. Default: 3.", default: "" },
  { flag: "rt",    label: "Render threads",   type: "number", tab: "execution", help: "Parallel page-render threads for multi-page PDFs. Default: min(4, CPU cores - 1); set 1 to disable.", default: "" },
  { flag: "rf",    label: "File name filter", type: "text",   tab: "execution", help: "Only test files whose name matches this regular expression, e.g. Lorem.* tests Lorem1.pdf and Lorem2.pdf.", default: "" },
  { flag: "debug", label: "Debug prints", type: "checkbox", tab: "execution", help: "Print verbose debug output to the log.", default: false },
  { flag: "log",   label: "Verbose log",  type: "checkbox", tab: "execution", help: "Enable detailed Applitools SDK logging.", default: false },
  { flag: "lf",    label: "Log file",     type: "text",     tab: "execution", help: "Deprecated — set the log path with the APPLITOOLS_LOG_DIR environment variable instead.", default: "" },
  // Matching
  { flag: "ms", label: "Match size",          type: "text",     tab: "matching", help: "Resize images and PDF pages to a target size before comparing. e.g. 1000x (by width), x600 (by height), 1000x600 (exact — may distort).", default: "" },
  { flag: "mt", label: "Match timeout (ms)",  type: "number",   tab: "matching", help: "Match/retry timeout in milliseconds. Minimum 500. Default: 500.", default: "" },
  { flag: "id", label: "Ignore displacement", type: "checkbox", tab: "matching", help: "Ignore position shifts of elements that only moved.", default: false },
  { flag: "as", label: "Auto-save failed",    type: "checkbox", tab: "matching", help: "Automatically accept new baselines on failure. Use with care — saves without human review.", default: false },
  { flag: "pt", label: "Prompt new tests",    type: "checkbox", tab: "matching", help: "Don't auto-save new tests; review and save them manually on the dashboard.", default: false },
  { flag: "ic", label: "Image cut",           type: "imagecut", tab: "matching", help: "Trim pixels from each side before comparing. Order: header, footer, left, right (leave any blank to skip).", default: "" },
  { flag: "rc", label: "Region capture",      type: "regions",  tab: "matching", help: "Test only a sub-region of each image/PDF instead of the whole page. One region: x, y, width, height.", default: "" },
  { flag: "ac", label: "Accessibility",       type: "text",     tab: "matching", help: "Run accessibility validation. Format Level:Guideline, e.g. AA:WCAG_2_0 (AA|AAA, WCAG_2_0|WCAG_2_1).", default: "" },
  // Batch & Branch
  { flag: "br",  label: "Branch",             type: "text",     tab: "batch", help: "Branch name for this run (for branch-based baselines).", default: "" },
  { flag: "pb",  label: "Parent branch",      type: "text",     tab: "batch", help: "Parent branch name, used when working with branches.", default: "" },
  { flag: "bn",  label: "Baseline",           type: "text",     tab: "batch", help: "Custom baseline name to compare against.", default: "" },
  { flag: "bb",  label: "Baseline branch",    type: "text",     tab: "batch", help: "Baseline branch name.", default: "" },
  { flag: "fb",  label: "Flat batch name",    type: "text",     tab: "batch", help: "Put all discovered tests into one batch with this name. Append <>BATCH_ID to also set the batch id.", default: "" },
  { flag: "sq",  label: "Sequence name",      type: "text",     tab: "batch", help: "Batch sequence name for grouped insights on the Applitools dashboard.", default: "" },
  { flag: "fn",  label: "Forced name",        type: "text",     tab: "batch", help: "Force every test to this name — makes all files match a single baseline.", default: "" },
  { flag: "nc",  label: "Notify on complete", type: "checkbox", tab: "batch", help: "Send a batch notification when the run finishes.", default: false },
  { flag: "dcb", label: "Don't close batch",  type: "checkbox", tab: "batch", help: "Leave the batch open after the run instead of auto-closing it.", default: false },
  // Regions
  { flag: "ir",  label: "Ignore regions",              type: "regions", tab: "regions", help: "Areas excluded from comparison, applied to all pages.", default: "" },
  { flag: "cr",  label: "Content regions",             type: "regions", tab: "regions", help: "Areas compared as content (ignore styling), applied to all pages.", default: "" },
  { flag: "lr",  label: "Layout regions",              type: "regions", tab: "regions", help: "Areas compared by layout only (ignore text and content), applied to all pages.", default: "" },
  { flag: "ari", label: "Accessibility: ignore",       type: "regions", tab: "regions", help: "Accessibility ignore regions. Leave empty for the full page.", default: "" },
  { flag: "arr", label: "Accessibility: regular text", type: "regions", tab: "regions", help: "Accessibility regular-text regions. Empty = full viewport.", default: "" },
  { flag: "arl", label: "Accessibility: large text",   type: "regions", tab: "regions", help: "Accessibility large-text regions. Empty = full viewport.", default: "" },
  { flag: "arb", label: "Accessibility: bold text",    type: "regions", tab: "regions", help: "Accessibility bold-text regions. Empty = full viewport.", default: "" },
  { flag: "arg", label: "Accessibility: graphic",      type: "regions", tab: "regions", help: "Accessibility graphics regions. Empty = full viewport.", default: "" },
  // PDF & Documents
  { flag: "di", label: "DPI",               type: "number",   tab: "pdf", help: "Rendering quality (dots per inch) for PDF pages. Higher is sharper but slower. Default: 250.", default: "" },
  { flag: "sp", label: "Selected pages",    type: "text",     tab: "pdf", help: "Which PDF pages to test, e.g. 1,2,5,7,10-15. Default: all pages.", default: "" },
  { flag: "tp", label: "Trim print margins", type: "checkbox", tab: "pdf", help: "Remove printer margins (crop marks, slug area) from PDF pages before comparing — detects the trim area from TrimBox metadata or crop marks. For a fixed-size crop, use the CLI: -tp WxH in PDF points.", default: false },
  { flag: "pn", label: "Page numbers",      type: "checkbox", tab: "pdf", help: "Preserve the original test names when testing only selected pages.", default: false },
  { flag: "pp", label: "PDF password",      type: "password", tab: "pdf", help: "Password for opening protected PDF files.", default: "" },
  { flag: "st", label: "Split steps",       type: "checkbox", tab: "pdf", help: "Split a multi-page document into individual single-step tests.", default: false },
  { flag: "nf",  label: "Normalize Latin fonts",    type: "checkbox", tab: "pdf", help: "Rewrite Latin-script PDF text to Helvetica 12pt before rendering — ignores font/typography changes. Japanese text is left untouched (use Normalize Japanese fonts). Invalidates existing baselines.", default: false },
  { flag: "nfj", label: "Normalize Japanese fonts", type: "checkbox", tab: "pdf", help: "Rewrite Japanese text (Hiragana, Katakana, Kanji) to a bundled Noto Sans JP 12pt before rendering — ignores Japanese font changes. Combine with Normalize Latin fonts for full normalization. Invalidates existing baselines.", default: false },
  { flag: "lo", label: "Legacy file order", type: "checkbox", tab: "pdf", help: "Use pre-2.0 file ordering to stay compatible with older baselines.", default: false },
  // Connection
  { flag: "s",  label: "Server URL",         type: "text",     tab: "connection", help: "Applitools server URL. Also settable via APPLITOOLS_SERVER_URL.", default: "" },
  { flag: "p",  label: "Proxy",              type: "proxy",    tab: "connection", help: "Proxy server, with optional username and password.", default: "" },
  { flag: "dv", label: "Disable SSL verify", type: "checkbox", tab: "connection", help: "Disable SSL certificate validation. Insecure — only use if your network requires it.", default: false },
  // Watermark
  { flag: "rwauto", label: "Auto-detect watermark", type: "checkbox", tab: "watermark", help: "Auto-detect and strip a shared watermark across PDFs in each folder by its fill color. Needs at least 2 same-source PDFs per folder.", default: false },
  { flag: "rwo",    label: "Don't upload — only produce cleaned PDFs locally", type: "watermarkout", tab: "watermark", help: "Writes watermark-removed PDFs to a folder on your machine and skips uploading to Applitools — use it to preview the result. Requires Auto-detect.", default: "" },
  // Downloads
  { flag: "vk", label: "View key",       type: "password", tab: "downloads", help: "Applitools enterprise view-key. Required to download diffs, images, or GIFs.", default: "" },
  { flag: "gd", label: "Download diffs", type: "checkbox", tab: "downloads", help: "Download diff images of the failed steps.", default: false },
  { flag: "gi", label: "Download images",type: "checkbox", tab: "downloads", help: "Download the baseline and actual images of the failed steps.", default: false },
  { flag: "gg", label: "Download GIFs",  type: "checkbox", tab: "downloads", help: "Download animated GIFs of the failed steps.", default: false },
  { flag: "of", label: "Output folder",  type: "text",     tab: "downloads", help: "Custom output path (or path template) for the downloaded results.", default: "" },
];

export type ControlType =
  | "text"
  | "password"
  | "number"
  | "checkbox"
  | "select"
  | "regions"
  | "proxy"
  | "properties"
  | "imagecut";

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

export const TABS: { id: TabId; label: string }[] = [
  { id: "metadata", label: "Metadata" },
  { id: "execution", label: "Execution" },
  { id: "matching", label: "Matching" },
  { id: "batch", label: "Batch & Branch" },
  { id: "regions", label: "Regions" },
  { id: "pdf", label: "PDF & Documents" },
  { id: "connection", label: "Connection" },
  { id: "watermark", label: "Watermark" },
  { id: "downloads", label: "Downloads" },
];

export const OPTION_SPECS: OptionSpec[] = [
  // Metadata
  { flag: "a",  label: "App name",      type: "text",       tab: "metadata", default: "" },
  { flag: "os", label: "Host OS",       type: "text",       tab: "metadata", default: "" },
  { flag: "ap", label: "Host app",      type: "text",       tab: "metadata", default: "" },
  { flag: "en", label: "Environment",   type: "text",       tab: "metadata", default: "" },
  { flag: "dn", label: "Device name",   type: "text",       tab: "metadata", default: "" },
  { flag: "vs", label: "Viewport size", type: "text",       tab: "metadata", help: "WxH e.g. 1000x600", default: "" },
  { flag: "pr", label: "Properties",    type: "properties", tab: "metadata", default: "" },
  // Execution
  { flag: "th",    label: "Threads",      type: "number",   tab: "execution", default: "" },
  { flag: "debug", label: "Debug prints", type: "checkbox", tab: "execution", default: false },
  { flag: "log",   label: "Verbose log",  type: "checkbox", tab: "execution", default: false },
  { flag: "lf",    label: "Log file",     type: "text",     tab: "execution", default: "" },
  // Matching
  { flag: "ms", label: "Match size",          type: "text",     tab: "matching", help: "1000x / x600 / 1000x600", default: "" },
  { flag: "mt", label: "Match timeout (ms)",  type: "number",   tab: "matching", default: "" },
  { flag: "id", label: "Ignore displacement", type: "checkbox", tab: "matching", default: false },
  { flag: "as", label: "Auto-save failed",    type: "checkbox", tab: "matching", default: false },
  { flag: "pt", label: "Prompt new tests",    type: "checkbox", tab: "matching", default: false },
  { flag: "ic", label: "Image cut",           type: "imagecut", tab: "matching", default: "" },
  { flag: "rc", label: "Region capture",      type: "regions",  tab: "matching", default: "" },
  { flag: "ac", label: "Accessibility",       type: "text",     tab: "matching", help: "Level:Guideline e.g. AA:WCAG_2_0", default: "" },
  // Batch & Branch
  { flag: "br",  label: "Branch",             type: "text",     tab: "batch", default: "" },
  { flag: "pb",  label: "Parent branch",      type: "text",     tab: "batch", default: "" },
  { flag: "bn",  label: "Baseline",           type: "text",     tab: "batch", default: "" },
  { flag: "bb",  label: "Baseline branch",    type: "text",     tab: "batch", default: "" },
  { flag: "fb",  label: "Flat batch name",    type: "text",     tab: "batch", default: "" },
  { flag: "sq",  label: "Sequence name",      type: "text",     tab: "batch", default: "" },
  { flag: "fn",  label: "Forced name",        type: "text",     tab: "batch", default: "" },
  { flag: "nc",  label: "Notify on complete", type: "checkbox", tab: "batch", default: false },
  { flag: "dcb", label: "Don't close batch",  type: "checkbox", tab: "batch", default: false },
  // Regions
  { flag: "ir",  label: "Ignore regions",              type: "regions", tab: "regions", default: "" },
  { flag: "cr",  label: "Content regions",             type: "regions", tab: "regions", default: "" },
  { flag: "lr",  label: "Layout regions",              type: "regions", tab: "regions", default: "" },
  { flag: "ari", label: "Accessibility: ignore",       type: "regions", tab: "regions", default: "" },
  { flag: "arr", label: "Accessibility: regular text", type: "regions", tab: "regions", default: "" },
  { flag: "arl", label: "Accessibility: large text",   type: "regions", tab: "regions", default: "" },
  { flag: "arb", label: "Accessibility: bold text",    type: "regions", tab: "regions", default: "" },
  { flag: "arg", label: "Accessibility: graphic",      type: "regions", tab: "regions", default: "" },
  // PDF & Documents
  { flag: "di", label: "DPI",               type: "number",   tab: "pdf", default: "" },
  { flag: "sp", label: "Selected pages",    type: "text",     tab: "pdf", help: "e.g. 1,3,5-10", default: "" },
  { flag: "pn", label: "Page numbers",      type: "checkbox", tab: "pdf", default: false },
  { flag: "pp", label: "PDF password",      type: "password", tab: "pdf", default: "" },
  { flag: "st", label: "Split steps",       type: "checkbox", tab: "pdf", default: false },
  { flag: "nf", label: "Normalize fonts",   type: "checkbox", tab: "pdf", default: false },
  { flag: "lo", label: "Legacy file order", type: "checkbox", tab: "pdf", default: false },
  // Connection
  { flag: "s",  label: "Server URL",         type: "text",     tab: "connection", default: "" },
  { flag: "p",  label: "Proxy",              type: "proxy",    tab: "connection", default: "" },
  { flag: "dv", label: "Disable SSL verify", type: "checkbox", tab: "connection", default: false },
  // Watermark
  { flag: "rw",     label: "Remove watermark text", type: "text",     tab: "watermark", default: "" },
  { flag: "rwauto", label: "Auto-detect watermark", type: "checkbox", tab: "watermark", default: false },
  { flag: "rwo",    label: "Standalone out dir",    type: "text",     tab: "watermark", help: "Writes cleaned PDFs and exits — no upload", default: "" },
  // Downloads
  { flag: "vk", label: "View key",        type: "password", tab: "downloads", default: "" },
  { flag: "gd", label: "Download diffs",  type: "checkbox", tab: "downloads", default: false },
  { flag: "gi", label: "Download images", type: "checkbox", tab: "downloads", default: false },
  { flag: "gg", label: "Download GIFs",   type: "checkbox", tab: "downloads", default: false },
  { flag: "of", label: "Output folder",   type: "text",     tab: "downloads", default: "" },
];

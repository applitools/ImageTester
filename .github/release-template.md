ImageTester GUI — visual testing for images, PDFs & documents, powered by Applitools Eyes. Download the installer for your machine, double-click it, and the app opens in your browser. Nothing else to install.

## Which file do I download?

| Your machine | File |
|---|---|
| Windows | `ImageTester-{{VERSION}}-Windows.msi` |
| Mac with Apple Silicon | `ImageTester-{{VERSION}}-macOS-AppleSilicon.dmg` |
| Mac with Intel | `ImageTester-{{VERSION}}-macOS-Intel.dmg` |
| Linux (Debian/Ubuntu) | `ImageTester-{{VERSION}}-Linux.deb` |

Not sure which Mac you have? Apple menu → **About This Mac**: "Chip: Apple M…" means Apple Silicon; "Processor: Intel…" means Intel.

## Command line (CLI jars)

Prefer the CLI? Grab a jar and run it with Java 11+: `java -jar ImageTester_{{VERSION}}.jar -k <api-key> [options]`

| Environment | File |
|---|---|
| Any (largest, bundles every platform) | `ImageTester_{{VERSION}}.jar` |
| Windows x64 | `ImageTester_{{VERSION}}_Windows.jar` |
| Mac Intel | `ImageTester_{{VERSION}}_Mac.jar` |
| Mac Apple Silicon | `ImageTester_{{VERSION}}_MacArm.jar` |
| Linux x64 | `ImageTester_{{VERSION}}_Linux.jar` |
| Alpine Linux | `ImageTester_{{VERSION}}_Alpine.jar` |
| Linux ARM | `ImageTester_{{VERSION}}_Arm.jar` |

<!-- UNSIGNED-START -->
## First launch

This demo build isn't code-signed yet (signed builds are planned), so your computer will warn you the first time you run it:

- **Windows** shows "Windows protected your PC". Click **More info**, then **Run anyway**.
- **macOS** blocks the first launch. Open **System Settings → Privacy & Security**, scroll down, click **Open Anyway** next to ImageTester, and confirm.
- **macOS** may also ask to use "confidential information stored in Applitools ImageTester in your keychain" — that's the app reading back the API key it saved securely in your Keychain. Click **Always Allow**. (Unsigned builds re-ask after each update; signed builds won't.)
<!-- UNSIGNED-END -->

## Using the app

Launching ImageTester opens a page in your browser. Paste your Applitools API key, choose the file or folder to test, and click **Run test**. Results stream in live, with a link to your results on the Applitools dashboard.

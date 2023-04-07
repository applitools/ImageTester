# Image Tester [ ![Download](https://img.shields.io/github/v/tag/applitools/ImageTester?label=Download&style=plastic) ](https://github.com/applitools/ImageTester/releases/latest)

ImageTester is a Cli tool to perform visual tests on images or PDF files. 

If you don't have your Applitools account yet, 
please [sign up first]("https://applitools.com/sign-up/") 
and get your Applitools api-key that will be used next to execute the tests.

The tool can be invoked on a single file or a complex folder structure with mixed content.
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

## Execution
The tool build in java and requires minimal set of parameters the minimal command will look as follow:

>java -jar ImageTester.jar -k [api-key]

\* In the minimal set of parameters will assume that the search folder is the execution folder.

+ Required parameters:
    + `-k [api-key]` - Applitools api key can also be set by environment variable APPLITOOLS_API_KEY
+ Optional parameters and flags:
    + `-f [path]` - A path to target folder or file
    + `-a [app-name]` - Set the application name under directoryTest; default = ImageTester
    + `-p [http://proxy{,user,pass}]` - Set proxy and optional username + password, can also be set by environment variable APPLITOOLS_PROXY
    + `-s [server]` - Set Applitools server url , can also be set by environment variable APPLITOOLS_SERVER_URL
    + `-ml [match-level]` - Set the comparison level, one from Strict/Content/Layout; Default = Strict
    + `-br [branch]` - Set the branch
    + `-pb [parent-branch]` - Set the parent branch
    + `-bn [baseline]` - Set custom baseline name
    + `-vs [WidthxHeight]` - Set the viewport size identifier
    + `-lf [log-file]` - DEPRECATED. Please set log file path with the environment variable *APPLITOOLS_LOG_PATH* <br>
      + **The logs are automatically created and saved to:**
      + Mac/Linux - `$TMPDIR/applitools-logs/`
      + Windows (PowerShell) - `$env:TEMP/applitools-logs/`
      + **The log directory can now be specified using this environment variable:**
      + `APPLITOOLS_LOG_DIR=<path>`
    + `-os [osname]` - Set custom os
    + `-ap [browser name]` - Set browser or equivalent hosting application name
    + `-th [number]` - Specify max. concurrent workers (Threads). default= 3
    + `-fn [testName]` - Force all test names to be specific name. This will force all tests to be matched with a single baseline.
    + `-fb [batchName]` - Set unified flat batch to contain all the discovered tests regardless their hierarchy.
      + To add batch id to the flat batch use the following syntax:`-fb BATCH_NAME<>BATCH_ID`
    + `-sq [sequenceName]` - Set batch sequenceName for unified insights in applitools' dashboard.
    + `-ms [{width x height}]` - Match the size of the images to a specific width and/or height ie. `1000x`- adjust by width, `x600`-adjust by height, `1000x600`- fit to the exact size (note, if both provided, may loose proportions)
    + `-ic [{header,footer,left,right}]` - Set pixels to cut from each side (one or more) in the format [header,footer,left,right], including missing notations ie: `-ic ,,10,4`
    + `-nc` - Send batch notification on complete.
    + `-as` - Set automatic save on failures
    + `-st` - Split steps to individual tests
    + `-id` - Ignore displacement of shifting elements.
    + `-pn` - Prompt new tests, new tests will not be saved automatically, the user will have to review and save manually.
    + `-dv` - Disable SSL certificate validation. !!!Unsecured!!!
    + `-lo` - Use legacy files order complying with baselines that were created with versions below 2.0
    + `-ac [Level:GuidelineVer]` - Set accessibility validation and optionally it's arguments split by semicolons ':' default: "AA:WCAG_2_0", available options: [AA|AAA:WCAG_2_0|WCAG_2_1]
    + `-dcb` - ImageTester will not automatically close batch(es) when test is complete.
    + `-mp` - ImageTester will read from and execute tests based on the batch mapper CSV.
    + `-te` - ImageTester will throw an exception if Eyes detects a mismatch or failure. <br>
    + `-mt` - Sets match timeout and retry timeout (default=500). <br>
    + `-dn` - Sets device name for test in dashboard. <br>
    + `-ir` - Ignore regions will be applied to all pages. <br>
      **Example:** `-ir "300,300,500,100|500,500,200,200"` will create ignored regions at <br> 
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-cr` - Apply content regions to all pages. <br>
      **Example:** `-cr "300,300,500,100|500,500,200,200"` will create content regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-lr` - Layout regions will be applied to all pages. <br>
      **Example:** `-ir "300,300,500,100|500,500,200,200"` will create layout regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-ari` - Accessibility ignore regions will be applied to all pages. <br>
      **Example:** `-ari "300,300,500,100|500,500,200,200"` will create accessibility ignore regions at <br>
        + x:300, y:300, with width:500, height:100 and <br>
        + x:500, y:500, with width:200, height:200 <br>
    + `-arr` - Accessibility regular text regions will be applied to all pages. Without coordinates, region will be the viewport size. <br>
      **Example:** `-arr "300,300,500,100|500,500,200,200"` will create accessibility regular text regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-arl` - Accessibility large text regions will be applied to all pages. Without coordinates, region will be the viewport size. <br>
      **Example:** `-arl "300,300,500,100|500,500,200,200"` will create accessibility large text regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-arb` - Accessibility bold text regions will be applied to all pages. Without coordinates, region will be the viewport size. <br>
      **Example:** `-arb "300,300,500,100|500,500,200,200"` will create accessibility bold text regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-arg` - Accessibility graphic regions will be applied to all pages. Without coordinates, region will be the viewport size. <br>
      **Example:** `-arg "300,300,500,100|500,500,200,200"` will create accessibility graphics regions at <br>
      + x:300, y:300, with width:500, height:100 and <br>
      + x:500, y:500, with width:200, height:200 <br>
    + `-rc` - Capture only a particular region of PDFs/Images. <br>
      **Example:** `-rc "200,500,1000,1000"` instructs ImageTester to only test region with: <br>
      + x:200, y:500, with width:1000, height:1000

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
filePath|testName|app|os|browser|viewport|matchsize|pages|matchLevel|layoutRegions|contentRegions|ignoreRegions|
docs/a.pdf|Test1|AppA|Linux|Chrome|1024x748||1|Strict||800,800,100,100|300,300,300,100|
docs/a.pdf|Test2|AppA||||x748|1-3|Layout|||500,500,500,100|
docs/b.pdf|Test3|AppB|||||2-5|200,200,500,500|||
```

###### For Documents (PDFs) only
+ `-di [dpi]` - Set the quality of the conversion on PDF files
+ `-sp [pages]` - Comma separated page numbers\range to include in PDF testing (ie: 1,2,5,7,10-15); Default all included
+ `-pp [password]` - The password if the PDF files protected
+ `-pn` - Preserve original directory test names when specifying pages

## Enterprise features in combination with [Eyes Utilities](https://github.com/yanirta/EyesUtilities)
Note: EyesUtilities temporarily removed for security re-architecture.

>java -jar ImageTester.jar -k [api-key] -vk [view-key] [options]
+ Required parameters:
    + `-k [api-key]` - Applitools api key
    + `-vk [view-key]` - Applitools enterprise view-key
+ Selective flags - Required one or more
    + `-gd` - Get diff images of the failed steps
    + `-gi` - Get images of the failed steps
    + `-gg` - Get animated gifs of the failed steps
+ Optional parameters and flags:
    + `-of [path]` - Specify custom output path or path-template
    
## CI/CD integration
Once setting the required environment variables, the ImageTester is able to pick them up and use to sync with the results
with other tools that out there for CI/CD integration.

The parameters:
+ `JOB_NAME` - The name of the job as it was set in CI/CD
+ `APPLITOOLS_BATCH_ID` - Job id, a unique identifier of the current job.

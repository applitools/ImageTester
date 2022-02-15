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
    + `-k [api-key]` - Applitools api key
+ Optional parameters and flags:
    + `-f [path]` - A path to target folder or file
    + `-a [app-name]` - Set the application name under directoryTest; default = ImageTester
    + `-p [http://proxy{,user,pass}]` - Set proxy and optional username + password
    + `-s [server]` - Set Applitools server url
    + `-ml [match-level]` - Set the comparison level, one from Strict/Content/Layout; Default = Strict
    + `-br [branch]` - Set the branch
    + `-pb [parent-branch]` - Set the parent branch
    + `-bn [baseline]` - Set custom baseline name
    + `-vs [WidthxHeight]` - Set the viewport size identifier
    + `-lf [log-file]` - Set log fine name to enable logging
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
    + `-lo` - Use legacy files order to comply with baselines that were created with versions below 2.0
    + `-ac [Level:GuidelineVer]` - Set accessibility validation and optionally it's arguments split by semicolumns ':' default: "AA:WCAG_2_0", available options: [AA|AAA:WCAG_2_0|WCAG_2_1]
    + `-dcb` - ImageTester will not automatically close batch(es) when test is complete.
    + `-mp` - ImageTester will read from and execute tests based on the batch mapper CSV.

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
    - `directory` - The name of the directory where the tests are located
    - `fileName`- The name of the pdf being tested
    - `pages`- Pages to be tested for this PDF. (ie: 1,2,5,7,10-15)
    - `testName` - The name of the test as it will appear on the Applitools dashboard (Optional)
    - `os` - The operating system to be tested on. This is optional
    - `app` - The app name of the test as it will appear on the Applitools dashboard (Optional)
    - `batchName` - The name of the batch as it will appear on the Applitools dashboard
  
    #### Sample CSV:
  ```
  filePath|testName|app|os|browser|viewport|matchsize|pages|matchLevel
  docs/a.pdf|Test1|AppA|Linux|Chrome|1024x748||1|Strict
  docs/a.pdf|Test2|AppA||||x748|1-3|Layout
  docs/b.pdf|Test3|AppB|||||2-5|  
  ```
  
  ###### For Documents (PDFs) only
    + `-di [dpi]` - Set the quality of the conversion on PDF files
    + `-sp [pages]` - Comma separated page numbers\range to include in PDF testing (ie: 1,2,5,7,10-15); Default all included
    + `-pp [password]` - The password if the PDF files protected
    + `-pn` - Preserve original directory test names when specifying pages

## Enterprise features in combination with [Eyes Utilities](https://github.com/yanirta/EyesUtilities)
By placing the Eyes-Utilities jar into the same folder as the com.yanirta.ImageTester, new enterprise api features
made possible by providing an enterprise read-key.

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

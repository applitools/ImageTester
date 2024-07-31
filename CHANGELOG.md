# Changelog

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
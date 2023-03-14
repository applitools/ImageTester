# Changelog

## Unreleased
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
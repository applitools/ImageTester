# Changelog

## Unreleased

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
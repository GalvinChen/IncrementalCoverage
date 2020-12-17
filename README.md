## IncrementalCoverage

IncrementalCoverage is an Android gradle plugin which you can use it to inspect the incremental code coverage.

### How to use

apply the plugin

```java
plugins {
    id 'com.android.application'
    id 'com.galvin.code-coverage' version '0.1'
}
```

define some code to generate the execution file

```java
try {
  RT.dumpCoverageData(new File(Environment.getExternalStorageDirectory() + "/coverage.exec"), false);
} catch (IOException e) {
  e.printStackTrace();
}
```

define your diff in local.properties

we use the command `git diff baseline revision -U0` to get diff source lines

```properties
revision=HEAD
baseline=HEAD~1
```

run the app, test your diff code

run gradle task `./gradlew generateCoverageReport` to get the coverage report

### Example

![](./captures/diff.png)

![](./captures/coverage.png)
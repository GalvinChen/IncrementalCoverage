package com.galvin.plugin.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffUtils {
    private static Pattern filePattern = Pattern.compile("diff --git a/(.+) b/(.+)");
    private static Pattern lineNumsPattern = Pattern.compile("^@@ -(\\d+),?(\\d+)? \\+(\\d+),?(\\d+)? @@.*");

    public static Map<String, List<Integer>> findChangedLinesPerFile(InputStream inputStream) {
        Map<String, List<Integer>> result = new HashMap<>();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            String currentFileName = null;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = filePattern.matcher(line);
                if (matcher.find()) {
                    // example: diff --git a/app/build.java b/app/build.java
                    // groups: [diff --git a/app/build.java b/app/build.java][app/build.java][app/build.java]
                    String newFilePath = matcher.group(2);
                    int lastSlashIndex = newFilePath.lastIndexOf("/");
                    String newFileName = newFilePath.substring(lastSlashIndex + 1);
                    if (newFileName.endsWith(".java") || newFileName.endsWith(".kt")) {
                        result.put(newFileName, new ArrayList<>());
                        currentFileName = newFileName;
                    } else {
                        currentFileName = null;
                    }
                    continue;
                }
                if (currentFileName == null) continue;
                matcher = lineNumsPattern.matcher(line);
                if (matcher.find()) {
                    // example: @@ -21,0 +23,1 @@ android {
                    // groups: [@@ -21,0 +23,1 @@ android {][21][0][23][1]
                    String newStartStr = matcher.group(3);
                    String newCountStr = matcher.group(4);
                    int newStart = Integer.parseInt(newStartStr);
                    int newCount;
                    if (newCountStr == null) {
                        newCount = 1;
                    } else {
                        newCount = Integer.parseInt(newCountStr);
                    }
                    if (newCount == 0) {
                        continue;
                    }
                    List<Integer> lineNums = result.get(currentFileName);
                    for (int i = 0; i < newCount; i++) {
                        lineNums.add(newStart + i);
                    }
                }
            }
        } catch (IOException e) {

        }
        return result;
    }
}

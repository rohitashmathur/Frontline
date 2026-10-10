package com.frontline.offline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only current release metadata is checked; historical evidence keeps its own version. */
public final class DocsVersionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]);
        String source = read(root,"app/src/main/java/com/frontline/offline/AppVersion.java");
        check(one(source,"public static final String NAME = \"([^\"]+)\";",1).equals(AppVersion.NAME),"Source version matches compiled version");
        check(one(source,"public static final int CODE = ([0-9]+);",1).equals(Integer.toString(AppVersion.CODE)),"Source version code matches compiled code");
        String gradle = read(root,"app/build.gradle");
        check(one(gradle,"(?m)^\\s*versionName '([^']+)'\\s*$",1).equals(AppVersion.NAME),"Gradle version name");
        check(one(gradle,"(?m)^\\s*versionCode ([0-9]+)\\s*$",1).equals(Integer.toString(AppVersion.CODE)),"Gradle version code");
        String build = read(root,"scripts/build-android.ps1");
        check(one(build,"'--version-name','([^']+)'",1).equals(AppVersion.NAME),"Local build version name");
        check(one(build,"'--version-code','([0-9]+)'",1).equals(Integer.toString(AppVersion.CODE)),"Local build version code");
        for (String file : new String[] {"README.md","docs/architecture.md","docs/release-v12-step2.md","docs/verification-v12-step2.md"}) {
            String doc = read(root,file);
            String metadata = "(?m)^Current source version: `([^`]+)` \\(version code `([0-9]+)`\\)\\.$";
            check(one(doc,metadata,1).equals(AppVersion.NAME),file+" current version");
            check(one(doc,metadata,2).equals(Integer.toString(AppVersion.CODE)),file+" current code");
        }
        check(one(read(root,"docs/release-v12-step2.md"),"\\A# V12 Step 2 / ([^\\r\\n]+)",1).equals(AppVersion.NAME),"Current release heading");
        System.out.println("PASS: "+checks+" current release metadata checks.");
    }
    private static String read(Path root,String file) throws Exception {
        return new String(Files.readAllBytes(root.resolve(file)),StandardCharsets.UTF_8);
    }
    private static String one(String source,String expression,int group) {
        Matcher match = Pattern.compile(expression).matcher(source);
        if (!match.find()) throw new AssertionError("Missing metadata: "+expression);
        String value = match.group(group);
        if (match.find()) throw new AssertionError("Duplicate metadata: "+expression);
        return value;
    }
    private static void check(boolean value,String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
}

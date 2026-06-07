# JVM Application Packaging Guide

This guide explains how to package and distribute the Compose Multiplatform JVM application (`composeApp`) for desktop users, either as a standalone runnable directory containing an `.exe` executable (without installer) or as a single executable JAR (Uber JAR).

---

## 1. Standalone Native Directory (Recommended)

This compiles the application into a standalone directory containing a native launcher executable (`com.damn.aisuper.exe` on Windows) and a custom, stripped-down JVM runtime. This option allows the application to run on any machine without requiring the user to have Java installed.

### Commands

To build a **debug** distribution:
```powershell
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.10"
.\gradlew.bat :composeApp:createDistributable
```

To build a **release** (optimized) distribution:
```powershell
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.10"
.\gradlew.bat :composeApp:createReleaseDistributable
```

### Output Location

The packaged distribution will be written to:
```text
composeApp/build/compose/binaries/main/app/com.damn.aisuper/
```

### Contents of the Package
* **`com.damn.aisuper.exe`**: The standalone native executable launcher.
* **`app/`**: Folder containing the application's compiled resources, assets, and dependencies.
* **`runtime/`**: A lightweight, bundled JRE.

To distribute the application, simply compress (e.g., zip) the entire `com.damn.aisuper` directory and send it to your users. They can run `com.damn.aisuper.exe` directly.

---

## 2. Single Executable JAR (Uber JAR)

If you prefer distributing a single file instead of a folder, you can build a self-contained "Uber JAR" (fat JAR). Note that this option requires the end user to have a Java Runtime Environment (JRE) installed on their system to run the app.

### Commands

To build a **debug** Uber JAR:
```powershell
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.10"
.\gradlew.bat :composeApp:packageUberJarForCurrentOS
```

To build a **release** Uber JAR:
```powershell
$env:JAVA_HOME="c:\Users\ink\.jdks\jbr-21.0.10"
.\gradlew.bat :composeApp:packageReleaseUberJarForCurrentOS
```

### Output Location

The single compiled `.jar` file will be generated at:
```text
composeApp/build/compose/binaries/main/default/com.damn.aisuper-1.0.0.jar
```

### Running the JAR
Users can launch the application from the command line using:
```cmd
java -jar com.damn.aisuper-1.0.0.jar
```

---

## 3. Running with a Custom Applet

You can run the JVM desktop application with a custom external applet by passing the path to either an **applet ZIP file** or an **unzipped applet root directory** as a command-line argument. The app will automatically determine the format and load the resources accordingly.

### Format and Directory Structure Requirements
The custom applet must contain the entry point manifest file `applet.json` directly at its root. Any internal resources (scripts, layouts, etc.) referenced in `applet.json` under `"files/"` paths (e.g. `"files/feature1.js"`) must be located inside a sibling `files/` directory.

The argument passed to the app **must be the parent folder/ZIP root** containing both `applet.json` and the `files/` folder:

```text
my-custom-applet/                <-- Pass this path as the command-line argument
├── applet.json                  <-- (Entry point)
└── files/
    ├── feature1.js              <-- (Loaded as "files/feature1.js")
    └── feature1.json            <-- (Loaded as "files/feature1.json")
```

If the provided path is invalid or the files fail to load, the application will display a detailed error message on the screen rather than silently falling back to the default bundled applet.

### Running with standalone executable `.exe`:
```powershell
# From directory
.\com.damn.aisuper.exe "C:\path\to\my-applet-directory"

# From ZIP file
.\com.damn.aisuper.exe "C:\path\to\my-applet.zip"
```

### Running with Gradle (Development):
```powershell
# From directory
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet-directory"

# From ZIP file
.\gradlew.bat :composeApp:run --args="C:\path\to\my-applet.zip"
```


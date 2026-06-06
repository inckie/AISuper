# AI Super App

# General overview

Currently there are a number of so-called “super applications”, like Grab, WeChat, Uber Go, Yandex Go and so on. These applications include functionality of what used to be multiple separate applications, like banking app, taxi app, food delivery app.  
The document proposes the next step of Super Application, where these separate applications, called “applets” can be built, loaded and executed dynamically based on user needs. Actual development of these applets is performed by strong “External” AI, that delivers a set of artifacts, which can be verified and signed. Super applications can load and execute these artifacts in safe sandbox environments, with permission guards.  
Rationale: while in theory any functionality provided by applet can be performed using “Strong” LLM AI with ad-hock UI “dashboards” for visual presentation, there is a number of drawbacks for that generic case:

* In many cases it's more reasonable to have specialized self contained “closed loop” UI applications, like “Podcast player” or “Spending Analyzer”  
* It’s not always comfortable to use chat mode to operate the app.  
* Running “Strong” AI is expensive and wasteful for performing basic functionality of say Audio Player  
* Since “Strong” LLM by nature is stochastic, there is no guarantee of it having reproducible and safe behavior, which is usually expected from the typical purpose-built application.

# Building blocks

| Block | Provided by |
| :---- | :---- |
| Widget – visual component in UI: Label, Button, Slider, Image, Map, Video View, and so on. | Application runtime |
| Layout – spatial arrangement of Widgets or other Layouts. | Applet |
| Screen – container to present layout in a specific way: modal, regular | Application runtime |
| Navigation – stack of screens, can be hierarchical | Application runtime |
| Module – isolated and permission guarded building block providing MCP-like interface | Application runtime |
| Action – abstracted call to the module, widget update, navigation to another screen and so on. Some actions are “embedded”, some are provided by Modules and Features. | Application runtime, applet |
| Use Case – primitive case of interaction with a system, like “pressing a button on specific screen when in specific state performs specific action” | Applet |
| Flow – a state machine that implements some Use Case or a group of Use Cases. **Represented as a graph so it can be verified**. | Applet |
| Pipeline – a chain of actions calls (both native and pure JS) with parameters passed as blobs and dictionaries. **Represented as a graph so it can be verified**. | Applet |
| Block – a sandboxed script that implements logical parts of the Flows, like calling of the actions or interaction with Modules. Can be run both locally or remotely. | Applet |
| Flow State – a set of variables and objects that keeps the current state of the Flow and all required data. Can be interacted with using actions. | Applet |
| Blob – text or binary named piece of data that can be shared/transferred between modules to avoid passing it though interfaces. AI modules should operate with blobs when dealing with data. For example, if large HTTP response must be passed to the Block, it's done wia Blob. Can be permanent (file or database), cache files, or in-memory | Applet |
| Global State – State that keeps information shared by all Flows. Access can be protected by permissions. | Applet |
| Feature – a connection of Widgets, Layouts, Screens, Flows | Applet |
| Permission – receiving user approval to perform specific action using a specific module in the context of the specific Flow or Use Case. Can be one time or permanent. | Applet |
| Applet – a full set of artifacts: Layouts, screens, Use Cases, Flows, and Blocks that work together as a single application from user point of view | Applet |

Every Layout file, Flow graph file, block script file, and entire feature can be digitally signed.  
Expected list of base Modules:

* HTTP calls module: can perform REST queries.  
* Audio Player (can play in the background)  
* Video Player (rich video player screen, can also have additional layouts on top, and include blocks as regular screen)  
* In-App purchases module  
* GPS module

# Runtime environment

“Super application” resembles a typical flexible Game engine, that can load external layouts, present screens in required way, execute Blocks in the sandboxes, persist Flow states, and request permissions. Can be UI, headless or both (background services inside Android application).  
Application provides base Actions, like Present screen, and implements passing of data inside and between Flows.  
The application has a built-in set of modules, like Audio Player, Video Player, Purchases, GPS, Http calls.  
The application also provides sandboxes to test each building block (Layout, Flow) or interaction with modules in isolation.  
Each block and module should come with a set of test cases for the sandbox. For modules it\`s a pre-generated layout with a list of cases that can be performed against the module, like “Start playing a song using Audio Player module”, “Receive GPS coordinates”, “Load available in-app purchases list”, “Post local notification” and so on.

# Process of designing or expanding an applet

* The user provides an initial idea for the feature or even entire “applet”.  
* “External” AI with role “Feature designer” creates an initial specification and saves it.  
* New session “External” AI with role “Developer” analyzes specification and generates a set of artifacts: Widgets, Layouts, Flows and Blocks.  
* New session of External AI with role “verificator” and optionally human verifies generated artifacts. There are additional tools for verification of each block.  
* After these steps are complete, artifacts are exported and then loaded into the runtime environment application.

# Reference implementation details

* App and UI framework: Compose Multiplatform  
* Flow graph format: XState  
* Block script format: JavaScript  
* Initial Block JavaScript engine: https://github.com/alexzhirkevich/keight

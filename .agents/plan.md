# MVP Plan: AI Super App - Echo Chat Prototype

This plan outlines the steps to build a Minimum Viable Product (MVP) for the AI Super App, focusing on a basic "Echo Chat Applet" to demonstrate the core architecture: Layouts, Widgets, Blocks (Scripts), and Runtime integration.

## Goal
Create a prototype applet that displays a simple chat interface. When the user types text and presses "Send", the text is processed by a JavaScript Block (simulating an AI/Backend response) and echoed back to the conversation view.

## Phase 1: Layout & Runtime Basics
- [ ] **Define JSON Layout Format**: Create a simple JSON schema to represent the UI structure.
    -   Supported Widgets:
        -   `Text` (for displaying messages/answers)
        -   `TextField` (for user input)
        -   `Button` (to trigger actions)
        -   `Column/Row` (for basic arrangement)
- [ ] **Create Prototype Layout File**: Write a `echo_chat.json` file representing the chat interface.
- [ ] **Implement Layout Parser & Renderer**:
    -   Create a parser in Kotlin (Compose Multiplatform) to read the JSON.
    -   Dynamically map JSON objects to Compose UI functions (`Text`, `TextField`, `Button`).
    -   Render the UI on the screen (Android/Desktop/iOS).

## Phase 2: JavaScript Engine Integration
- [ ] **Select & Integrate JS Engine**:
    -   Evaluate and integrate a Kotlin Multiplatform compatible JavaScript engine (e.g., KJS, Rhino, or J2V8/QuickJS if needed, or keeping it simple with a lightweight choice like `Rhino` or similar compatible with the target). *Note: The spec mentioned `https://github.com/alexzhirkevich/keight`.*
- [ ] **Create Bridge Interface**:
    -   Define how Kotlin passes data to JS.
    -   Define how JS passes data/commands back to Kotlin (e.g., `updateWidget(id, value)`).

## Phase 3: Block Implementation (The "Brain")
- [ ] **Define Block Script**: Write a `echo_block.js` script.
    -   Logic: Receive input text -> Append "Echo: " -> Return/Update UI.
- [ ] **Connect UI Events to Script**:
    -   Update the Layout format to allow binding events (e.g., `onClick` in JSON) to specific JS functions or Block execution.
    -   Implement the event listener in the Runtime to trigger the JS engine.

## Phase 4: Data Binding & State (Simplified)
- [ ] **State Management**:
    -   Simple variable storage in the runtime (or JS context) to hold the chat history.
    -   Update the `Text` widget with the new history after the "Echo" response.

## Execution Steps
1.  Define the JSON layout structure.
2.  Implement the JSON rendering engine in `composeApp`.
3.  Set up the JS engine environment.
4.  Implement the "Echo" logic in JS.
5.  Wire it all together: UI renders -> User inputs -> Button Click -> JS runs -> JS updates UI.


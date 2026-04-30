# Miami Metromover Module Tests

Focused Node.js tests for parser logic and module wiring.

## Run

```bash
cd modules-ts/tests
node --test *.test.mjs
```

Or with npm scripts:

```bash
cd modules-ts/tests
npm test
```

## Files kept

- `metromover.test.mjs` - parser/math/SVG unit tests
- `integration.test.mjs` - export + wiring checks
- `e2e.test.mjs` - mocked end-to-end flow
- `xml-format-variations.test.mjs` - XML format behavior checks

## Notes

- Runtime validation for Keight remains in JVM tests under:
  - `composeApp/src/commonTest/kotlin/com/damn/aisuper/engine/KeightJSModuleIntegrationTest.kt`
- Long-form troubleshooting notes were moved into `.agents/js-modules-creation-skill.md`.


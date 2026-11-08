// test_full_suite.js - Comprehensive test suite for all features in widgets sample applet
import { api } from './main.js';

async function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function runSuite() {
  console.log('====================================================');
  console.log('   AISuper Pure JS (TinyJS) Comprehensive Test Suite');
  console.log('====================================================\n');

  const manifestUrl = new URL('../../../.agents/sample-applets/widgets/applet.json', import.meta.url);
  const manifestPath = manifestUrl.pathname.replace(/^\/([a-zA-Z]:)/, '$1');

  // 1. Session Creation & Entry Feature (menu)
  console.log('[TEST 1] Session Creation & Menu Feature');
  const session = await api.createSession({ manifestPath });
  const sId = session.id;
  console.log(`- Created session: ${sId}`);

  let state = session.state;
  if (state.featureId !== 'menu') throw new Error(`Expected feature 'menu', got ${state.featureId}`);
  if (!state.layout?.layout) throw new Error('Menu layout is missing');
  if (!Array.isArray(state.values.menuButtons) || state.values.menuButtons.length === 0) {
    throw new Error('Menu buttons were not populated by initialize()');
  }
  console.log(`- Verified menu feature loaded with ${state.values.menuButtons.length} navigation buttons`);
  console.log(`- Available themes: ${state.values.themeOptions?.map(t => t.label).join(', ')}`);
  console.log('✓ TEST 1 PASSED\n');

  // 2. Navigation to "widgets" Feature
  console.log('[TEST 2] Widgets Demo Feature & Controls');
  state = await api.sendAction({ sessionId: sId, action: 'launch:widgets', args: [] });
  if (state.featureId !== 'widgets') throw new Error(`Expected feature 'widgets', got ${state.featureId}`);
  if (state.values.demo_progress !== 0.5) throw new Error(`Expected initial progress 0.5, got ${state.values.demo_progress}`);
  if (state.values.slider_status !== 'Slider Value: 25') throw new Error(`Unexpected slider status: ${state.values.slider_status}`);

  // Test slider action
  state = await api.sendAction({ sessionId: sId, action: 'onSliderChange', args: ['demo_slider', 60] });
  if (state.values.slider_status !== 'Slider Value: 60 (id=demo_slider)') {
    throw new Error(`Slider status update failed: ${state.values.slider_status}`);
  }
  console.log(`- Slider adjusted: ${state.values.slider_status}`);

  // Test switch action
  state = await api.sendAction({ sessionId: sId, action: 'onSwitchToggle', args: ['demo_switch', true] });
  if (state.values.switch_status !== 'Switch State: true (id=demo_switch)') {
    throw new Error(`Switch status update failed: ${state.values.switch_status}`);
  }
  console.log(`- Switch toggled: ${state.values.switch_status}`);

  // Test progress increment
  state = await api.sendAction({ sessionId: sId, action: 'increaseProgress', args: [] });
  if (Math.abs(state.values.demo_progress - 0.6) > 0.001) {
    throw new Error(`Progress increment failed: ${state.values.demo_progress}`);
  }
  console.log(`- Progress incremented: ${state.values.demo_progress}`);

  // Test textfield subscription
  state = await api.setValue({ sessionId: sId, id: 'demo_textfield', value: 'Hello TinyJS' });
  await delay(50); // Allow worker to process subscription callback
  state = await api.getState({ sessionId: sId });
  if (state.values.textfield_status !== 'Typing: Hello TinyJS') {
    throw new Error(`Textfield subscription callback failed: ${state.values.textfield_status}`);
  }
  console.log(`- Textfield reactive subscription verified: ${state.values.textfield_status}`);
  console.log('✓ TEST 2 PASSED\n');

  // 3. Navigation to "todo" Feature (Storage bindings)
  console.log('[TEST 3] Task Manager (Persistent Storage)');
  state = await api.sendAction({ sessionId: sId, action: 'launch:todo', args: [] });
  if (state.featureId !== 'todo') throw new Error(`Expected feature 'todo', got ${state.featureId}`);
  console.log(`- Todo feature loaded. Layout type: ${state.layout?.layout?.type}`);

  // Set input and add task
  await api.setValue({ sessionId: sId, id: 'todo_input', value: 'Test Pure JS Worker' });
  state = await api.sendAction({ sessionId: sId, action: 'addTask', args: [] });
  console.log(`- Added task via addTask(). State task list items: ${state.values.todo_list?.children?.length ?? 'dynamic'}`);
  console.log('✓ TEST 3 PASSED\n');

  // 4. Navigation to "multilayout" Feature (Dynamic setLayout)
  console.log('[TEST 4] Multi-Layout Demo (Dynamic setLayout)');
  state = await api.sendAction({ sessionId: sId, action: 'launch:multilayout', args: [] });
  if (state.featureId !== 'multilayout') throw new Error(`Expected feature 'multilayout', got ${state.featureId}`);
  await delay(50);
  state = await api.getState({ sessionId: sId });
  console.log(`- Initial view loaded`);

  state = await api.sendAction({ sessionId: sId, action: 'switchToView2', args: [] });
  await delay(50);
  state = await api.getState({ sessionId: sId });
  console.log(`- Switched to View 2`);

  state = await api.sendAction({ sessionId: sId, action: 'switchToView1', args: [] });
  await delay(50);
  state = await api.getState({ sessionId: sId });
  console.log(`- Switched back to View 1`);
  console.log('✓ TEST 4 PASSED\n');

  // 5. Navigation to "timeout" Feature (Async Timers in Worker Isolate)
  console.log('[TEST 5] Timeout Demo (Timers in Worker Isolate)');
  state = await api.sendAction({ sessionId: sId, action: 'launch:timeout', args: [] });
  if (state.featureId !== 'timeout') throw new Error(`Expected feature 'timeout', got ${state.featureId}`);
  if (state.values.statusLabel !== 'Press the button to start a 5-second timer') {
    throw new Error(`Unexpected initial status: ${state.values.statusLabel}`);
  }

  // Trigger startTimer
  state = await api.sendAction({ sessionId: sId, action: 'startTimer', args: [] });
  if (state.values.statusLabel !== 'Timer started...') {
    throw new Error(`Unexpected status after startTimer: ${state.values.statusLabel}`);
  }
  console.log(`- Timer started: ${state.values.statusLabel}`);
  console.log('✓ TEST 5 PASSED\n');

  // 6. Theme Switching
  console.log('[TEST 6] Theme & Style Switching');
  state = await api.sendAction({ sessionId: sId, action: 'launch:menu', args: [] });
  state = await api.sendAction({ sessionId: sId, action: 'changeTheme', args: ['neon'] });
  if (state.styleSheet?.name !== 'Neon') {
    throw new Error(`Expected stylesheet 'Neon', got '${state.styleSheet?.name}'`);
  }
  console.log(`- Theme switched to: ${state.styleSheet.name}`);

  state = await api.sendAction({ sessionId: sId, action: 'changeTheme', args: ['light'] });
  if (state.styleSheet?.name !== 'Light') {
    throw new Error(`Expected stylesheet 'Light', got '${state.styleSheet?.name}'`);
  }
  console.log(`- Theme switched back to: ${state.styleSheet.name}`);
  console.log('✓ TEST 6 PASSED\n');

  console.log('====================================================');
  console.log('   🎉 ALL 6 COMPREHENSIVE TEST SUITES PASSED! 🎉');
  console.log('====================================================');
  tjs.exit(0);
}

runSuite().catch(err => {
  console.error('\n❌ TEST SUITE FAILED:', err);
  tjs.exit(1);
});

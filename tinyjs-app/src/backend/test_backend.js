// test_backend.js - Test suite for AISuper backend on txiki.js
import { api } from './main.js';

async function runTests() {
  console.log('=== Step 1: Health Check ===');
  const health = await api.health();
  console.log('Health:', JSON.stringify(health));

  console.log('\n=== Step 2: Create Session (Widgets Applet) ===');
  const manifest = 'd:/Work/Mobile/Android/AISuper/.agents/sample-applets/widgets/applet.json';
  const session = await api.createSession({ manifestPath: manifest });
  console.log('Session ID:', session.id);
  console.log('Feature:', session.state.featureId);
  console.log('Themes count:', session.state.values.themeOptions ? session.state.values.themeOptions.length : 0);
  console.log('Menu buttons count:', session.state.values.menuButtons ? session.state.values.menuButtons.length : 0);

  if (session.state.featureId !== 'menu') {
    throw new Error(`Expected entry feature to be 'menu', got '${session.state.featureId}'`);
  }

  console.log('\n=== Step 3: Launch Widgets Demo ===');
  const widgetsState = await api.sendAction({
    sessionId: session.id,
    action: 'launch:widgets',
    args: []
  });
  console.log('New Feature:', widgetsState.featureId);
  console.log('Demo Progress initial:', widgetsState.values.demo_progress);
  console.log('Slider Status initial:', widgetsState.values.slider_status);

  if (widgetsState.featureId !== 'widgets') {
    throw new Error(`Expected feature 'widgets', got '${widgetsState.featureId}'`);
  }

  console.log('\n=== Step 4: Test increaseProgress Action ===');
  const progressState = await api.sendAction({
    sessionId: session.id,
    action: 'increaseProgress',
    args: []
  });
  console.log('Demo Progress after increase:', progressState.values.demo_progress);

  console.log('\n=== Step 5: Test onSliderChange Action ===');
  const sliderState = await api.sendAction({
    sessionId: session.id,
    action: 'onSliderChange',
    args: ['demo_slider', 85]
  });
  console.log('Slider Status:', sliderState.values.slider_status);

  console.log('\n=== Step 6: Test onDropdownChange Action ===');
  // First set value of demo_dropdown
  await api.setValue({
    sessionId: session.id,
    id: 'demo_dropdown',
    value: 'Option B'
  });
  const dropdownState = await api.sendAction({
    sessionId: session.id,
    action: 'onDropdownChange',
    args: []
  });
  console.log('Dropdown Status:', dropdownState.values.dropdown_status);

  console.log('\n=== Step 7: Back to Menu and Change Theme ===');
  const menuState = await api.sendAction({
    sessionId: session.id,
    action: 'launch:menu',
    args: []
  });
  console.log('Back on feature:', menuState.featureId);

  const darkThemeState = await api.sendAction({
    sessionId: session.id,
    action: 'changeTheme',
    args: ['dark']
  });
  console.log('Active theme sheet name:', darkThemeState.styleSheet?.name);
  console.log('Theme picker value:', darkThemeState.values.themePicker);

  console.log('\n>>> ALL BACKEND TESTS PASSED SUCCESSFULLY! <<<');
  tjs.exit(0);
}

runTests().catch(err => {
  console.error('\n!!! TEST FAILED !!!', err);
  tjs.exit(1);
});

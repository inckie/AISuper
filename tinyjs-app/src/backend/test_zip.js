// test_zip.js - Test loading applet from a .zip file directly
import { api } from './main.js';

async function testZip() {
  console.log('Testing Applet loading from ZIP archive...');
  const zipPath = 'd:/Work/Mobile/Android/AISuper/.agents/plans/pure-js-tinyjs/working_materials/widgets_test.zip';

  const session = await api.createSession({ manifestPath: zipPath });
  console.log('Created session from ZIP:', session.id);
  console.log('Resolved manifest:', session.manifestPath);
  console.log('Entry feature:', session.state.featureId);
  console.log('Theme options count:', session.state.values.themeOptions?.length);
  console.log('Menu buttons count:', session.state.values.menuButtons?.length);

  // Test action inside the zipped applet
  const widgetsState = await api.sendAction({
    sessionId: session.id,
    action: 'launch:widgets',
    args: []
  });
  console.log('Launched widgets feature from ZIP. Initial progress:', widgetsState.values.demo_progress);

  const incState = await api.sendAction({
    sessionId: session.id,
    action: 'increaseProgress',
    args: []
  });
  console.log('Progress after increase:', incState.values.demo_progress);

  console.log('>>> ZIP APPLET TEST PASSED! <<<');
  tjs.exit(0);
}

testZip().catch(err => {
  console.error('ZIP Test Failed:', err);
  tjs.exit(1);
});

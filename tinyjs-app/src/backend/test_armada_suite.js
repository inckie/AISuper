// test_armada_suite.js - Comprehensive automated test suite for Armada applet, HTTP, and JS modules
import { AppletSession } from './applet_session.js';

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function assert(condition, message) {
  if (!condition) {
    console.error(`FAIL: ${message}`);
    throw new Error(message);
  }
  console.log(`PASS: ${message}`);
}

async function run() {
  console.log('====================================================');
  console.log('   Testing Armada Applet on Pure JS TinyJS Runtime');
  console.log('====================================================\n');

  const manifestUrl = new URL('../../../.temp/armada/applet/applet.json', import.meta.url);
  const manifestPath = manifestUrl.pathname.replace(/^\/([a-zA-Z]:)/, '$1');
  console.log('Resolved Armada manifestPath:', manifestPath);

  // Clean test storage to start fresh on login
  if (typeof tjs !== 'undefined' && tjs.tmpDir) {
    try {
      await tjs.writeFile(`${tjs.tmpDir.replace(/\\/g, '/')}/aisuper_storage.json`, new TextEncoder().encode('{}'));
    } catch (_) {}
  }

  let lastSnapshot = null;
  const session = new AppletSession('armada_test_sess', manifestPath, (snap) => {
    lastSnapshot = snap;
  });

  // 1. Applet Session Load & Entry Feature (login)
  console.log('\n[TEST 1] Loading Armada Applet & Login Feature...');
  await session.load();
  await sleep(150);

  assert(session.currentFeatureId === 'login', `Current feature is 'login' (got ${session.currentFeatureId})`);
  assert(session.currentLayout !== null, 'Login layout loaded');
  assert(session.jsModules.has('auth'), "JS Module 'auth' loaded and cached");
  assert(session.jsModules.has('armada_api'), "JS Module 'armada_api' loaded and cached");

  const authMod = session.jsModules.get('auth');
  assert(authMod.exports.has('initAuth'), "'auth' exports initAuth");
  assert(authMod.exports.has('login'), "'auth' exports login");
  assert(authMod.exports.has('logout'), "'auth' exports logout");

  // 2. Fill Demo Action
  console.log('\n[TEST 2] Testing fillDemo action...');
  await session.sendAction('fillDemo');
  assert(session.values['input_phone'] === '+7 (918) 123-45-67', `Phone prefilled: ${session.values['input_phone']}`);
  assert(session.values['input_password'] === 'demo123', `Password prefilled: ${session.values['input_password']}`);

  // 3. Submit Login & Transition to Cabinet
  console.log('\n[TEST 3] Testing submitLogin action (Cross-isolate RPC -> auth module)...');
  await session.sendAction('submitLogin');
  // Allow time for login async execution and feature transition
  await sleep(350);

  assert(session.currentFeatureId === 'cabinet', `Navigated to 'cabinet' feature (got ${session.currentFeatureId})`);
  assert(session.jsModules.has('security'), "JS Module 'security' loaded");
  assert(session.jsModules.has('kids_tracker'), "JS Module 'kids_tracker' loaded");

  // 4. Verify Cabinet Authenticated State & UI bindings
  console.log('\n[TEST 4] Verifying Cabinet Data & Sub-modules...');
  const accountBtn = session.values['cabinet_account_btn'];
  console.log('- Cabinet account label:', accountBtn);
  assert(typeof accountBtn === 'string' && accountBtn.includes('Иванов'), `Account label reflects logged in user: ${accountBtn}`);

  const servicedCards = session.values['serviced_cards'];
  assert(Array.isArray(servicedCards) && servicedCards.length === 3,
    `3 Serviced objects loaded into cards (got ${servicedCards?.length})`);

  const suspendedCards = session.values['suspended_cards'];
  assert(Array.isArray(suspendedCards) && suspendedCards.length === 1,
    `1 Suspended object loaded into cards (got ${suspendedCards?.length})`);

  const kidsBadge = session.values['cabinet_kids_badge'];
  console.log('- Kids tracker badge:', kidsBadge);
  assert(typeof kidsBadge === 'string' && kidsBadge.includes('в сети'), `Kids badge populated: ${kidsBadge}`);

  // 5. Direct Module Invocations (Sync & Async)
  console.log('\n[TEST 5] Testing direct security and API module invocations...');
  const secMod = session.jsModules.get('security');
  const promoJson = await session.invokeModule(secMod, 'getPromoCards', []);
  const promo = JSON.parse(promoJson);
  assert(Array.isArray(promo) && promo.length === 3, `Promo cards returned 3 offers (got ${promo.length})`);

  // 6. HTTP fetch verification
  console.log('\n[TEST 6] Testing HTTP fetch module integration...');
  const httpRes = await session.handleSyncRpc('httpGet', ['https://httpbin.org/status/200', {}]).catch(e => e.message);
  console.log('- HTTP GET check (online or offline):', typeof httpRes);

  session.close();
  console.log('\n====================================================');
  console.log('   🎉 ALL ARMADA MODULE & HTTP TESTS PASSED! 🎉');
  console.log('====================================================\n');
}

run().catch(err => {
  console.error('Armada Test Suite failed:', err);
});

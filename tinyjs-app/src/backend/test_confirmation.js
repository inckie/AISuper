// test_confirmation.js - End-to-end verification of modal confirmation dialogs in TinyJS isolate
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
  console.log('--- Testing Confirmation Dialog System ---');

  const manifestUrl = new URL('../../../.agents/sample-applets/widgets/applet.json', import.meta.url);
  const manifestPath = manifestUrl.pathname.replace(/^\/([a-zA-Z]:)/, '$1');
  console.log('Resolved manifestPath:', manifestPath);

  let lastSnapshot = null;

  const session = new AppletSession('test_sess', manifestPath, (snap) => {
    lastSnapshot = snap;
  });

  await session.load();
  await session.launchFeature('confirmation');
  await sleep(100);

  assert(session.values['lastResultLabel'] === 'Ready. Choose an action to test native modal dialogs.',
    'Initial label set correctly');

  // Test 1: Standard Confirmation (Confirm / true)
  console.log('\n[Test 1] Standard Confirmation -> Confirm');
  // Trigger action without awaiting it to finish, because it will suspend on await confirm(...)
  session.sendAction('demoStandardConfirm');
  await sleep(150);

  assert(lastSnapshot?.modal !== null && lastSnapshot?.modal !== undefined, 'Modal request dispatched');
  assert(lastSnapshot.modal.title === 'Save Changes?', `Title matches: ${lastSnapshot?.modal?.title}`);
  assert(lastSnapshot.modal.confirmText === 'Save', `Confirm text is Save: ${lastSnapshot?.modal?.confirmText}`);
  assert(lastSnapshot.modal.isDestructive === false, 'isDestructive is false');
  assert(session.values['lastResultLabel'] === 'Waiting for confirmation...', 'Worker is awaiting modal response');

  const modal1Id = lastSnapshot.modal.id;
  session.respondToModal(modal1Id, true);
  await sleep(150);

  assert(session.activeModal === null, 'Active modal cleared after response');
  assert(session.values['lastResultLabel'] === 'Result: Confirmed (Save). Changes saved successfully!',
    `Result label updated to confirmed: ${session.values['lastResultLabel']}`);

  // Test 2: Destructive Confirmation -> Cancel
  console.log('\n[Test 2] Destructive Confirmation -> Cancel');
  session.sendAction('demoDestructiveConfirm');
  await sleep(150);

  assert(lastSnapshot?.modal !== null, 'Destructive modal request dispatched');
  assert(lastSnapshot.modal.title === 'Delete Account Data?', `Destructive title: ${lastSnapshot.modal.title}`);
  assert(lastSnapshot.modal.isDestructive === true, 'isDestructive is true');

  const modal2Id = lastSnapshot.modal.id;
  session.respondToModal(modal2Id, false);
  await sleep(150);

  assert(session.values['lastResultLabel'] === 'Result: Cancelled. Deletion aborted safely.',
    `Result label updated to cancelled: ${session.values['lastResultLabel']}`);

  // Test 3: Simple String Confirmation -> Confirm
  console.log('\n[Test 3] Simple String Confirmation -> Confirm');
  session.sendAction('demoSimpleStringConfirm');
  await sleep(150);

  assert(lastSnapshot?.modal !== null, 'Simple string modal request dispatched');
  assert(lastSnapshot.modal.title === 'Quick Confirmation', `Simple title: ${lastSnapshot.modal.title}`);
  assert(lastSnapshot.modal.message === 'Proceed with default configuration?', `Simple message: ${lastSnapshot.modal.message}`);
  assert(lastSnapshot.modal.confirmText === 'Confirm', `Default confirm text is Confirm: ${lastSnapshot.modal.confirmText}`);
  assert(lastSnapshot.modal.cancelText === 'Cancel', `Default cancel text is Cancel: ${lastSnapshot.modal.cancelText}`);

  const modal3Id = lastSnapshot.modal.id;
  session.respondToModal(modal3Id, true);
  await sleep(150);

  assert(session.values['lastResultLabel'] === 'Result: Confirmed simple string prompt.',
    `Result label updated to simple confirm: ${session.values['lastResultLabel']}`);

  session.close();
  console.log('\nALL CONFIRMATION DIALOG TESTS PASSED SUCCESSFULLY!');
}

run().catch(err => {
  console.error('Test failed:', err);
});

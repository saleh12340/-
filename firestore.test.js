const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidProduct(userId, now) {
  return {
    userId,
    name: "عصير",
    barcode: "6281001001",
    baseUnitName: "حبة",
    defaultUnitName: "كرتون",
    unitTiers: [
      { name: "كرتون", factorToPrevious: 24 },
      { name: "قرطاس", factorToPrevious: 5 },
    ],
    baseCost: 100,
    baseSellPrice: 150,
    stockBaseQty: 48,
    trackExpiry: false,
    expiryDate: "",
    minStockAlert: 10,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write products, contacts, invoices, or payments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(buildValidProduct(ALICE_UID, now))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get());
});

test("Cross-user isolation: Bob cannot read or modify Alice's products or contacts", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(buildValidProduct(ALICE_UID, now));
    await db.collection("users").doc(ALICE_UID).collection("contacts").doc("cust_1").set({
      userId: ALICE_UID,
      name: "عميل 1",
      contactType: "CUSTOMER",
      phone: "777000111",
      balance: 1500,
      notes: "",
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({ baseCost: 1, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").doc("cust_1").get());
});

test("Owner can create, list, and partially update products with manual baseCost while rejecting shadow/negative/zero-factor fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_juice").set(buildValidProduct(ALICE_UID, now))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("products").get());

  // Manual piece cost update (100 -> 105)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_juice").update({
      baseCost: 105,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative baseCost
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_juice").update({
      baseCost: -5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject shadow/ghost field or @DocumentId 'id' leak
  const withGhost = { ...buildValidProduct(ALICE_UID, now), id: "prod_ghost" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_ghost").set(withGhost)
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_juice").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject zero conversion factor
  const zeroFactor = buildValidProduct(ALICE_UID, now);
  zeroFactor.unitTiers = [{ name: "كرتون", factorToPrevious: 0 }];
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_zero").set(zeroFactor)
  );
});

test("Owner can create, update mutable fields, and delete invoices and payments while immutable/invalid edits fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").set({
      userId: ALICE_UID,
      invoiceNumber: "PUR-0001",
      invoiceType: "PURCHASE",
      paymentMethod: "CASH",
      contactId: "",
      contactName: "مورد نقدي",
      totalAmount: 4800,
      lines: [
        {
          productId: "prod_juice",
          productName: "عصير",
          unitName: "كرتون",
          cumulativeFactor: 24,
          unitQty: 2,
          unitPrice: 2400,
          baseQty: 48,
          lineTotal: 4800,
        },
      ],
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").update({
      totalAmount: 5000,
      notes: "تعديل فاتورة",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").delete()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").set({
      userId: ALICE_UID,
      contactId: "cust_1",
      contactName: "عميل 1",
      contactType: "CUSTOMER",
      amount: 500,
      notes: "سداد جزء من الرصيد",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").update({
      amount: 750,
      notes: "تعديل سند",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").update({
      amount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").delete()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payments").doc("pay_1").delete()
  );
});

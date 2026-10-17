import test from "node:test";
import assert from "node:assert/strict";
import { cents, money } from "../src/lib/format.mjs";
test("decimal input converts without binary floating rounding", () => {
  assert.equal(cents("59.00"), 5900);
  assert.equal(cents("0.29"), 29);
  assert.equal(cents("100000"), 10000000);
  assert.equal(cents("0.01"), 1);
});
test("invalid or out of range amounts are rejected", () => {
  for (const x of [
    "0",
    "-1",
    "1.001",
    "1e3",
    "NaN",
    "Infinity",
    "100000.01",
    "01",
    "",
    "0.001",
  ])
    assert.throws(() => cents(x));
});
test("cents are displayed as yuan", () => assert.match(money(5900), /59\.00/));

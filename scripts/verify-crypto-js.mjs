import fs from "node:fs";
import vm from "node:vm";

const source = fs.readFileSync(new URL("../app/src/main/assets/js/crypto-js-4.2.0.js", import.meta.url), "utf8");
const context = {};
vm.createContext(context);
vm.runInContext(source, context, { timeout: 5000 });

const vectors = new Map([
  ["", "d41d8cd98f00b204e9800998ecf8427e"],
  ["abc", "900150983cd24fb0d6963f7d28e17f72"],
]);
for (const [input, expected] of vectors) {
  const actual = vm.runInContext(`CryptoJS.MD5(${JSON.stringify(input)}).toString()`, context, { timeout: 1000 });
  if (actual !== expected) throw new Error(`MD5 vector failed for ${JSON.stringify(input)}: ${actual}`);
}
console.log(`CryptoJS MD5 vectors passed (${vectors.size})`);

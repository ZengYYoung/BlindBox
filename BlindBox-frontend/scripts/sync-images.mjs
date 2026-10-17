import { readdir, mkdir, copyFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const source = path.resolve(root, "../images");
const target = path.resolve(root, "public/images");
await mkdir(target, { recursive: true });
const files = (await readdir(source)).filter((name) => name.endsWith(".png"));
await Promise.all(
  files.map((name) =>
    copyFile(path.join(source, name), path.join(target, name)),
  ),
);
console.log("Synced " + files.length + " project images.");

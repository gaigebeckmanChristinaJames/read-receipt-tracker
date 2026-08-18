// KernelSU WebUI API 3.x, vendored from the official `kernelsu` package.
// Keeping this small local module makes the installed module self-contained.
let callbackCounter = 0;

function callbackName(prefix) {
  return `${prefix}_callback_${Date.now()}_${callbackCounter++}`;
}

export function exec(command, options = {}) {
  return new Promise((resolve, reject) => {
    const name = callbackName("exec");
    window[name] = (errno, stdout, stderr) => {
      resolve({ errno, stdout: stdout || "", stderr: stderr || "" });
      delete window[name];
    };
    try {
      if (typeof globalThis.ksu?.exec !== "function") {
        throw new Error("ksu.exec is not available");
      }
      globalThis.ksu.exec(command, JSON.stringify(options), name);
    } catch (error) {
      delete window[name];
      reject(error);
    }
  });
}

export function toast(message) {
  if (typeof globalThis.ksu?.toast === "function")
    globalThis.ksu.toast(message);
}

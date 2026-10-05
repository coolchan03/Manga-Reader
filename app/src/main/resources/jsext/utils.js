/*
 * Derived from Mangayomi (https://github.com/kodjodevf/mangayomi), Apache License 2.0.
 * File: lib/eval/javascript/utils.dart
 * This is the JavaScript half of Mangayomi's extension runtime, kept as close to the original as
 * possible so that unmodified Mangayomi JS extensions run. The host half is implemented in Kotlin.
 */
console.log = function (message) {
    if (typeof message === "object") {
         message = JSON.stringify(message);
      }
    sendMessage("log", JSON.stringify([message.toString()]));
};
console.warn = function (message) {
    if (typeof message === "object") {
         message = JSON.stringify(message);
      }
    sendMessage("log", JSON.stringify([message.toString()]));
};
console.error = function (message) {
    if (typeof message === "object") {
         message = JSON.stringify(message);
      }
    sendMessage("log", JSON.stringify([message.toString()]));
};
String.prototype.substringAfter = function(pattern) {
    const startIndex = this.indexOf(pattern);
    if (startIndex === -1) return this.substring(0);

    const start = startIndex + pattern.length;
    return this.substring(start);
}

String.prototype.substringAfterLast = function(pattern) {
    return this.split(pattern).pop();
}

String.prototype.substringBefore = function(pattern) {
    const endIndex = this.indexOf(pattern);
    if (endIndex === -1) return this.substring(0);

    return this.substring(0, endIndex);
}

String.prototype.substringBeforeLast = function(pattern) {
    const endIndex = this.lastIndexOf(pattern);
    if (endIndex === -1) return this.substring(0);
    return this.substring(0, endIndex);
}

String.prototype.substringBetween = function(left, right) {
    let startIndex = 0;
    let index = this.indexOf(left, startIndex);
    if (index === -1) return "";
    let leftIndex = index + left.length;
    let rightIndex = this.indexOf(right, leftIndex);
    if (rightIndex === -1) return "";
    startIndex = rightIndex + right.length;
    return this.substring(leftIndex, rightIndex);
}

function cryptoHandler(text, iv, secretKeyString, encrypt) {
    return sendMessage(
        "cryptoHandler",
        JSON.stringify([text, iv, secretKeyString, encrypt])
    );
}
function encryptAESCryptoJS(plainText, passphrase) {
    return sendMessage(
        "encryptAESCryptoJS",
        JSON.stringify([plainText, passphrase])
    );
}
function decryptAESCryptoJS(encrypted, passphrase) {
    return sendMessage(
        "decryptAESCryptoJS",
        JSON.stringify([encrypted, passphrase])
    );
}
function decryptAESGCM(encrypted, keyHex, ivHex, tagHex = "") {
    return sendMessage(
        "decryptAESGCM",
        JSON.stringify([encrypted, keyHex, ivHex, tagHex])
    );
}
function deobfuscateJsPassword(inputString) {
    return sendMessage(
        "deobfuscateJsPassword",
        JSON.stringify([inputString])
    );
}
function unpackJsAndCombine(scriptBlock) {
    return sendMessage(
        "unpackJsAndCombine",
        JSON.stringify([scriptBlock])
    );
}
function unpackJs(packedJS) {
    return sendMessage(
        "unpackJs",
        JSON.stringify([packedJS])
    );
}
function parseDates(value, dateFormat, dateFormatLocale) {
    return sendMessage(
        "parseDates",
        JSON.stringify([value, dateFormat, dateFormatLocale])
    );
}
async function evaluateJavascriptViaWebview(url, headers, scripts) {
    return await sendMessage(
        "evaluateJavascriptViaWebview",
        JSON.stringify([url, headers, scripts])
    );
}
async function parseEpub(bookName, url, headers) {
    return JSON.parse(await sendMessage(
        "parseEpub",
        JSON.stringify([bookName, url, headers])
    ));
}
async function parseEpubChapter(bookName, url, headers, chapterTitle) {
    return await sendMessage(
        "parseEpubChapter",
        JSON.stringify([bookName, url, headers, chapterTitle])
    );
}

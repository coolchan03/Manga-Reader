/*
 * Derived from Mangayomi (https://github.com/kodjodevf/mangayomi), Apache License 2.0.
 * File: lib/eval/javascript/extractors.dart
 * This is the JavaScript half of Mangayomi's extension runtime, kept as close to the original as
 * possible so that unmodified Mangayomi JS extensions run. The host half is implemented in Kotlin.
 */
async function sibnetExtractor(url, prefix) {
    const result = await sendMessage(
        "sibnetExtractor",
        JSON.stringify([url, prefix])
    );
    return JSON.parse(result);
}
async function myTvExtractor(url) {
    const result = await sendMessage(
        "myTvExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function okruExtractor(url) {
    const result = await sendMessage(
        "okruExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function voeExtractor(url, quality) {
    const result = await sendMessage(
        "voeExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function vidBomExtractor(url) {
    const result = await sendMessage(
        "vidBomExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function streamlareExtractor(url, prefix, suffix) {
    const result = await sendMessage(
        "streamlareExtractor",
        JSON.stringify([url, prefix, suffix])
    );
    return JSON.parse(result);
}
async function sendVidExtractor(url, headers, prefix) {
    const result = await sendMessage(
        "sendVidExtractor",
        JSON.stringify([url, JSON.stringify(headers), prefix])
    );
    return JSON.parse(result);
}
async function yourUploadExtractor(url, headers, name, prefix) {
    const result = await sendMessage(
        "yourUploadExtractor",
        JSON.stringify([url, JSON.stringify(headers), name, prefix])
    );
    return JSON.parse(result);
}
async function gogoCdnExtractor(url) {
    const result = await sendMessage(
        "gogoCdnExtractor",
        JSON.stringify([url])
    );
    return JSON.parse(result);
}
async function doodExtractor(url, quality) {
    const result = await sendMessage(
        "doodExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function streamTapeExtractor(url, quality) {
    const result = await sendMessage(
        "streamTapeExtractor",
        JSON.stringify([url, quality])
    );
    return JSON.parse(result);
}
async function mp4UploadExtractor(url, headers, prefix, suffix) {
    const result = await sendMessage(
        "mp4UploadExtractor",
        JSON.stringify([url, JSON.stringify(headers), prefix, suffix])
    );
    return JSON.parse(result);
}
async function streamWishExtractor(url, prefix) {
    const result = await sendMessage(
        "streamWishExtractor",
        JSON.stringify([url, prefix])
    );
    return JSON.parse(result);
}
async function filemoonExtractor(url, prefix, suffix) {
    const result = await sendMessage(
        "filemoonExtractor",
        JSON.stringify([url, prefix, suffix])
    );
    return JSON.parse(result);
}
async function quarkVideosExtractor(url, cookie) {
    const result = await sendMessage(
        "quarkVideosExtractor",
        JSON.stringify([url, cookie])
    );
    return JSON.parse(result);
}
async function ucVideosExtractor(url, cookie) {
    const result = await sendMessage(
        "ucVideosExtractor",
        JSON.stringify([url, cookie])
    );
    return JSON.parse(result);
}
async function quarkFilesExtractor(urls, cookie) {
    const result = await sendMessage(
        "quarkFilesExtractor",
        JSON.stringify([urls, cookie])
    );
    return result;
}
async function ucFilesExtractor(urls, cookie) {
    const result = await sendMessage(
        "ucFilesExtractor",
        JSON.stringify([urls, cookie])
    );
    return result;
}

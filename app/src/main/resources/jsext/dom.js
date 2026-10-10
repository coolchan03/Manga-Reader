/*
 * Derived from Mangayomi (https://github.com/kodjodevf/mangayomi), Apache License 2.0.
 * File: lib/eval/javascript/dom_selector.dart
 * This is the JavaScript half of Mangayomi's extension runtime, kept as close to the original as
 * possible so that unmodified Mangayomi JS extensions run. The host half is implemented in Kotlin.
 */
class Document {
    constructor(html) {
        this.html = html;
    }
    getElement(type) {
        const key = sendMessage(
            "get_doc_element",
            JSON.stringify([this.html, type])
        );
        return new Element(key);
    }
    get body() {
        return this.getElement('body');
    }
    get documentElement() {
        return this.getElement('documentElement');
    }
    get head() {
        return this.getElement('head');
    }
    get parent() {
        return this.getElement('parent');
    }
    getString(type) {
        return sendMessage(
            "get_doc_string",
            JSON.stringify([this.html, type]));
    }
    get text() {
        return this.getString('text');
    }
    get outerHtml() {
        return this.getString('outerHtml');
    }
    selectFirst(selector) {
        const key = sendMessage(
            "doc_select_first",
            JSON.stringify([this.html, selector])
        );
        return new Element(key);
    }
    select(selector) {
        let elements = [];
        JSON.parse(
            sendMessage("doc_select", JSON.stringify([this.html, selector]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    xpathFirst(xpath) {
        return sendMessage(
            "doc_xpath_first",
            JSON.stringify([this.html, xpath])
        );
    }
    xpath(xpath) {
        return JSON.parse(sendMessage(
            "doc_xpath",
            JSON.stringify([this.html, xpath]))
        );
    }
    getElementsListBy(type, name) {
        name = name || '';
        let elements = [];
        JSON.parse(sendMessage(
            "doc_get_elements_by",
            JSON.stringify([this.html, type, name]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    get children() {
        return this.getElementsListBy('children');
    }
    getElementsByTagName(name) {
        return this.getElementsListBy('getElementsByTagName', name);
    }
    getElementsByClassName(name) {
        return this.getElementsListBy('getElementsByClassName', name);
    }
    getElementById(id) {
        const key = sendMessage(
            "doc_get_element_by_id",
            JSON.stringify([this.html, id])
        );
        return new Element(key);
    }
    attr(attr) {
        return sendMessage(
            "doc_attr",
            JSON.stringify([this.html, attr])
        );
    }
    hasAttr(attr) {
        return sendMessage(
            "doc_has_attr",
            JSON.stringify([this.html, attr])
        );
    }
}

class Element {
    constructor(key) {
        this.key = key;
    }
    getString(type) {
        return sendMessage(
            "get_element_string",
            JSON.stringify([type, this.key])
        );
    }
    get text() {
        return this.getString("text");
    }
    get outerHtml() {
        return this.getString("outerHtml");
    }
    get innerHtml() {
        return this.getString("innerHtml");
    }
    get className() {
        return this.getString("className");
    }
    get localName() {
        return this.getString("localName");
    }
    get namespaceUri() {
        return this.getString("namespaceUri");
    }
    get getSrc() {
        return this.getString("getSrc");
    }
    get getImg() {
        return this.getString("getImg");
    }
    get getHref() {
        return this.getString("getHref");
    }
    get getDataSrc() {
        return this.getString("getDataSrc");
    }
    getElementSibling(type) {
        const key = sendMessage(
            "ele_element_sibling",
            JSON.stringify([type, this.key])
        );
        return new Element(key);
    }
    get previousElementSibling() {
        return this.getElementSibling("previousElementSibling");
    }
    get nextElementSibling() {
        return this.getElementSibling("nextElementSibling");
    }
    getElementsListBy(type, name) {
        name = name || '';
        let elements = [];
        JSON.parse(sendMessage(
            "ele_get_elements_by",
            JSON.stringify([type, name, this.key]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    get children() {
        return this.getElementsListBy('children');
    }
    getElementsByTagName(name) {
        return this.getElementsListBy('getElementsByTagName', name);
    }
    getElementsByClassName(name) {
        return this.getElementsListBy('getElementsByClassName', name);
    }
    xpath(xpath) {
        return JSON.parse(sendMessage(
            "xpath",
            JSON.stringify([xpath, this.key]))
        );
    }
    attr(attr) {
        return sendMessage(
            "ele_attr",
            JSON.stringify([attr, this.key])
        );
    }
    xpathFirst(xpath) {
        return sendMessage(
            "xpathFirst",
            JSON.stringify([xpath, this.key])
        );
    }
    selectFirst(selector) {
        const key = sendMessage(
            "ele_selectFirst",
            JSON.stringify([selector, this.key])
        );
        return new Element(key);
    }
    select(selector) {
        let elements = [];
        JSON.parse(
            sendMessage("ele_select", JSON.stringify([selector, this.key]))
        ).forEach((key) => {
            elements.push(new Element(key));
        });
        return elements;
    }
    hasAttr(attr) {
        return sendMessage(
            "ele_has_attr",
            JSON.stringify([this.html, attr])
        );
    }
}

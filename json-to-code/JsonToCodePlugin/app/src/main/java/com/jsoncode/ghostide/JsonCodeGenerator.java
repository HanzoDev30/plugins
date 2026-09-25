package com.jsoncode.ghostide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public final class JsonCodeGenerator {

  public enum Language {
    JAVA("Java", ".java"),
    KOTLIN("Kotlin", ".kt"),
    JAVASCRIPT("JavaScript", ".js"),
    TYPESCRIPT("TypeScript", ".ts"),
    PYTHON("Python", ".py"),
    PHP("PHP", ".php"),
    CSHARP("C#", ".cs"),
    CPP("C++", ".cpp"),
    GO("Go", ".go"),
    RUST("Rust", ".rs"),
    SWIFT("Swift", ".swift"),
    DART("Dart", ".dart"),
    RUBY("Ruby", ".rb"),
    SCALA("Scala", ".scala"),
    GROOVY("Groovy", ".groovy"),
    PERL("Perl", ".pl"),
    LUA("Lua", ".lua"),
    R("R", ".r"),
    SHELL("Shell", ".sh"),
    SQL("SQL", ".sql");

    private final String label;
    private final String extension;

    Language(String label, String extension) {
      this.label = label;
      this.extension = extension;
    }

    public String getLabel() {
      return label;
    }

    public String getExtension() {
      return extension;
    }
  }

  public static final class GeneratedCode {
    private final String text;
    private final String fileName;

    GeneratedCode(String text, String fileName) {
      this.text = text;
      this.fileName = fileName;
    }

    public String getText() {
      return text;
    }

    public String getFileName() {
      return fileName;
    }
  }

  private enum Kind {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    UNKNOWN
  }

  private static final Set<String> RESERVED =
      Set.of(
          "abstract", "and", "as", "assert", "async", "await", "break", "case", "catch", "class",
          "const", "continue", "def", "default", "del", "do", "elif", "else", "enum", "except",
          "export", "extends", "false", "final", "finally", "for", "from", "fun", "function",
          "go", "goto", "if", "implements", "import", "in", "instanceof", "interface", "is",
          "let", "match", "mod", "move", "new", "nil", "null", "object", "or", "package", "pass",
          "private", "protected", "public", "raise", "return", "static", "struct", "super",
          "switch", "this", "throw", "trait", "true", "try", "type", "typeof", "using", "var",
          "void", "when", "while", "with", "yield");

  private static final class Node {
    private final Kind kind;
    private final SortedMap<String, Node> fields = new TreeMap<>();
    private Node item;

    private Node(Kind kind) {
      this.kind = kind;
    }

    private Node(Kind kind, Node item) {
      this.kind = kind;
      this.item = item;
    }
  }

  private static final class FieldSpec {
    private final String jsonName;
    private final String name;
    private final Node node;

    private FieldSpec(String jsonName, String name, Node node) {
      this.jsonName = jsonName;
      this.name = name;
      this.node = node;
    }
  }

  private static final class Names {
    private final Set<String> used = new HashSet<>();

    private String take(String preferred) {
      String base = pascal(preferred);
      if (base.isEmpty()) {
        base = "Generated";
      }
      String candidate = base;
      int suffix = 2;
      while (!used.add(candidate)) {
        candidate = base + suffix++;
      }
      return candidate;
    }
  }

  private JsonCodeGenerator() {}

  public static GeneratedCode generate(String source, String className, Language language) {
    if (!isValidClassName(className)) {
      throw new IllegalArgumentException("Class name is not a valid identifier");
    }
    Node root = fromValue(parseJson(source));
    String text;
    switch (language) {
      case JAVA:
        text = generateJava(className, root);
        break;
      case KOTLIN:
        text = generateKotlin(className, root);
        break;
      case JAVASCRIPT:
        text = generateJavaScript(className, root);
        break;
      case TYPESCRIPT:
        text = generateTypeScript(className, root);
        break;
      case PYTHON:
        text = generatePython(className, root);
        break;
      case PHP:
        text = generatePhp(className, root);
        break;
      case CSHARP:
        text = generateCSharp(className, root);
        break;
      case CPP:
        text = generateCpp(className, root);
        break;
      case GO:
        text = generateGo(className, root);
        break;
      case RUST:
        text = generateRust(className, root);
        break;
      case SWIFT:
        text = generateSwift(className, root);
        break;
      case DART:
        text = generateDart(className, root);
        break;
      case RUBY:
        text = generateRuby(className, root);
        break;
      case SCALA:
        text = generateScala(className, root);
        break;
      case GROOVY:
        text = generateGroovy(className, root);
        break;
      case PERL:
        text = generatePerl(className, root);
        break;
      case LUA:
        text = generateLua(className, root);
        break;
      case R:
        text = generateR(className, root);
        break;
      case SHELL:
        text = generateShell(className, root);
        break;
      case SQL:
        text = generateSql(className, root);
        break;
      default:
        throw new IllegalArgumentException("Unsupported language");
    }
    return new GeneratedCode(text, className + language.getExtension());
  }

  public static boolean isValidJson(String source) {
    try {
      parseJson(source);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public static boolean isValidClassName(String value) {
    if (value == null || value.isEmpty()) {
      return false;
    }
    char first = value.charAt(0);
    if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_')) {
      return false;
    }
    for (int i = 1; i < value.length(); i++) {
      char c = value.charAt(i);
      if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
        return false;
      }
    }
    return !RESERVED.contains(value.toLowerCase(Locale.ROOT));
  }

  public static String suggestedClassName(String fileName) {
    if (fileName == null) {
      return "GeneratedModel";
    }
    String value = fileName.trim();
    int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
    if (slash >= 0) {
      value = value.substring(slash + 1);
    }
    int dot = value.lastIndexOf('.');
    if (dot > 0) {
      value = value.substring(0, dot);
    }
    value = pascal(value);
    return value.isEmpty() || !isValidClassName(value) ? "GeneratedModel" : value;
  }

  private static Object parseJson(String source) {
    String text = source == null ? "" : source.trim();
    if (text.isEmpty()) {
      throw new IllegalArgumentException("JSON is empty");
    }
    try {
      return new JSONTokener(text).nextValue();
    } catch (JSONException first) {
      int start = firstJsonStart(text);
      int end = lastJsonEnd(text);
      if (start < 0 || end <= start) {
        throw new IllegalArgumentException("Invalid JSON", first);
      }
      try {
        return new JSONTokener(text.substring(start, end + 1)).nextValue();
      } catch (JSONException second) {
        throw new IllegalArgumentException("Invalid JSON", second);
      }
    }
  }

  private static int firstJsonStart(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{' || c == '[') {
        return i;
      }
    }
    return -1;
  }

  private static int lastJsonEnd(String text) {
    for (int i = text.length() - 1; i >= 0; i--) {
      char c = text.charAt(i);
      if (c == '}' || c == ']') {
        return i;
      }
    }
    return -1;
  }

  private static Node fromValue(Object value) {
    if (value == null || value == JSONObject.NULL) {
      return new Node(Kind.NULL);
    }
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      Node node = new Node(Kind.OBJECT);
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        node.fields.put(key, fromValue(object.opt(key)));
      }
      return node;
    }
    if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      Node merged = null;
      for (int i = 0; i < array.length(); i++) {
        Object item = array.opt(i);
        if (item == null || item == JSONObject.NULL) {
          continue;
        }
        Node child = fromValue(item);
        merged = merged == null ? child : merge(merged, child);
      }
      return new Node(Kind.ARRAY, merged == null ? new Node(Kind.UNKNOWN) : merged);
    }
    if (value instanceof Boolean) {
      return new Node(Kind.BOOLEAN);
    }
    if (value instanceof Number) {
      return new Node(Kind.NUMBER);
    }
    if (value instanceof String) {
      return new Node(Kind.STRING);
    }
    return new Node(Kind.UNKNOWN);
  }

  private static Node merge(Node first, Node second) {
    if (first.kind == Kind.OBJECT && second.kind == Kind.OBJECT) {
      Node result = new Node(Kind.OBJECT);
      for (Map.Entry<String, Node> entry : first.fields.entrySet()) {
        result.fields.put(entry.getKey(), entry.getValue());
      }
      for (Map.Entry<String, Node> entry : second.fields.entrySet()) {
        Node existing = result.fields.get(entry.getKey());
        result.fields.put(entry.getKey(), existing == null ? entry.getValue() : merge(existing, entry.getValue()));
      }
      return result;
    }
    if (first.kind == Kind.ARRAY && second.kind == Kind.ARRAY) {
      return new Node(Kind.ARRAY, merge(first.item == null ? new Node(Kind.UNKNOWN) : first.item, second.item == null ? new Node(Kind.UNKNOWN) : second.item));
    }
    if (first.kind == second.kind) {
      return new Node(first.kind);
    }
    return new Node(Kind.UNKNOWN);
  }

  private static List<FieldSpec> fieldsFor(Node node) {
    List<FieldSpec> result = new ArrayList<>();
    if (node.kind == Kind.OBJECT) {
      for (Map.Entry<String, Node> entry : node.fields.entrySet()) {
        String raw = entry.getKey();
        result.add(new FieldSpec(raw, identifier(raw, false), entry.getValue()));
      }
    } else if (node.kind == Kind.ARRAY) {
      result.add(new FieldSpec("items", "items", node));
    } else {
      result.add(new FieldSpec("value", "value", node));
    }
    return result;
  }

  private static String generateJava(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder body = new StringBuilder();
    for (FieldSpec field : fields) {
      String type = javaType(field.node, className + pascal(field.jsonName), names, definitions);
      line(body, 1, "private " + type + " " + field.name + ";");
    }
    body.append('\n');
    appendJavaConstructor(body, className, fields, names, definitions, 1);
    for (FieldSpec field : fields) {
      String type = javaType(field.node, className + pascal(field.jsonName), names, definitions);
      String suffix = pascal(field.name);
      line(body, 1, "public " + type + " get" + suffix + "() {");
      line(body, 2, "return " + field.name + ";");
      line(body, 1, "}");
      line(body, 1, "public void set" + suffix + "(" + type + " value) {");
      line(body, 2, "this." + field.name + " = value;");
      line(body, 1, "}");
    }
    StringBuilder out = new StringBuilder("import java.util.*;\n\n");
    line(out, 0, "public class " + className + " {");
    out.append(body);
    out.append(definitions);
    line(out, 0, "}");
    return out.toString();
  }

  private static String javaType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        emitJavaClass(name, node, names, definitions, 1);
        return name;
      case ARRAY:
        return "List<" + javaType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "String";
      case NUMBER:
        return "Double";
      case BOOLEAN:
        return "boolean";
      case NULL:
      case UNKNOWN:
      default:
        return "Object";
    }
  }

  private static void emitJavaClass(String className, Node node, Names names, StringBuilder definitions, int level) {
    List<FieldSpec> fields = fieldsFor(node);
    StringBuilder body = new StringBuilder();
    for (FieldSpec field : fields) {
      String type = javaType(field.node, className + pascal(field.jsonName), names, definitions);
      line(body, level + 1, "private " + type + " " + field.name + ";");
    }
    if (!fields.isEmpty()) {
      body.append('\n');
      appendJavaConstructor(body, className, fields, names, definitions, level + 1);
    }
    line(definitions, level, "private static final class " + className + " {");
    definitions.append(body);
    line(definitions, level, "}");
  }

  private static void appendJavaConstructor(
      StringBuilder out,
      String className,
      List<FieldSpec> fields,
      Names names,
      StringBuilder definitions,
      int level) {
    StringBuilder parameters = new StringBuilder();
    StringBuilder assignments = new StringBuilder();
    for (FieldSpec field : fields) {
      if (parameters.length() > 0) {
        parameters.append(", ");
      }
      parameters.append(javaType(field.node, className + pascal(field.jsonName), names, definitions)).append(' ').append(field.name);
      line(assignments, level + 1, "this." + field.name + " = " + field.name + ";");
    }
    line(out, level, "public " + className + "(" + parameters + ") {");
    out.append(assignments);
    line(out, level, "}");
  }

  private static String generateKotlin(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder();
    appendKotlinClass(out, className, root, names, definitions, 0, false);
    out.append(definitions);
    return out.toString();
  }

  private static String kotlinType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendKotlinClass(definitions, name, node, names, definitions, 0, false);
        return name;
      case ARRAY:
        return "List<" + kotlinType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "String";
      case NUMBER:
        return "Double";
      case BOOLEAN:
        return "Boolean";
      case NULL:
        return "Any?";
      case UNKNOWN:
      default:
        return "Any";
    }
  }

  private static void appendKotlinClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions,
      int level,
      boolean nested) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, level, (nested ? "data class " : "data class ") + className + "(");
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      String type = kotlinType(field.node, className + pascal(field.jsonName), names, definitions);
      line(out, level + 1, "val " + field.name + ": " + type + (i + 1 == fields.size() ? "" : ","));
    }
    line(out, level, ")");
  }

  private static String generateJavaScript(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, "class " + className + " {");
    line(out, 1, "constructor(data = {}) {");
    for (FieldSpec field : fields) {
      line(out, 2, "this." + field.name + " = data[" + quote(field.jsonName) + "] ?? null;");
    }
    line(out, 1, "}");
    line(out, 1, "toJSON() {");
    line(out, 2, "return {");
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      line(out, 3, quote(field.jsonName) + ": this." + field.name + (i + 1 == fields.size() ? "" : ","));
    }
    line(out, 2, "};");
    line(out, 1, "}");
    line(out, 0, "}");
    out.append('\n');
    line(out, 0, "module.exports = " + className + ";");
    return out.toString();
  }

  private static String generateTypeScript(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder();
    appendTypeScriptInterface(out, className, root, names, definitions, 0, false);
    out.append(definitions);
    return out.toString();
  }

  private static String typeScriptType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendTypeScriptInterface(definitions, name, node, names, definitions, 0, false);
        return name;
      case ARRAY:
        return typeScriptType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + "[]";
      case STRING:
        return "string";
      case NUMBER:
        return "number";
      case BOOLEAN:
        return "boolean";
      case NULL:
      case UNKNOWN:
      default:
        return "unknown";
    }
  }

  private static void appendTypeScriptInterface(
      StringBuilder out,
      String interfaceName,
      Node node,
      Names names,
      StringBuilder definitions,
      int level,
      boolean nested) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, level, "export interface " + interfaceName + " {");
    for (FieldSpec field : fields) {
      String type = typeScriptType(field.node, interfaceName + pascal(field.jsonName), names, definitions);
      line(out, level + 1, field.name + ": " + type + ";");
    }
    line(out, level, "}");
  }

  private static String generatePython(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("from dataclasses import dataclass\nfrom typing import Any, List, Optional\n\n");
    appendPythonClass(out, className, root, names, definitions, false);
    out.append(definitions);
    return out.toString();
  }

  private static String pythonType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendPythonClass(definitions, name, node, names, definitions, true);
        return name;
      case ARRAY:
        return "List[" + pythonType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + "]";
      case STRING:
        return "str";
      case NUMBER:
        return "float";
      case BOOLEAN:
        return "bool";
      case NULL:
        return "Optional[Any]";
      case UNKNOWN:
      default:
        return "Any";
    }
  }

  private static void appendPythonClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions,
      boolean nested) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "@dataclass");
    line(out, 0, "class " + className + ":");
    if (fields.isEmpty()) {
      line(out, 1, "pass");
      return;
    }
    for (FieldSpec field : fields) {
      String type = pythonType(field.node, className + pascal(field.jsonName), names, definitions);
      line(out, 1, field.name + ": " + type);
    }
  }

  private static String generatePhp(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("<?php\n\n");
    appendPhpClass(out, className, root, names, definitions, false);
    out.append(definitions);
    return out.toString();
  }

  private static String phpType(Node node, String base, Names names, StringBuilder definitions) {
    if (node.kind == Kind.OBJECT) {
      String name = names.take(base);
      appendPhpClass(definitions, name, node, names, definitions, true);
      return name;
    }
    return "mixed";
  }

  private static void appendPhpClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions,
      boolean nested) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "class " + className);
    line(out, 0, "{");
    for (FieldSpec field : fields) {
      phpType(field.node, className + pascal(field.jsonName), names, definitions);
      line(out, 1, "public $" + field.name + ";");
    }
    line(out, 1, "public function __construct(array $data = [])");
    line(out, 1, "{");
    for (FieldSpec field : fields) {
      line(out, 2, "$this->" + field.name + " = $data[" + quotePhp(field.jsonName) + "] ?? null;");
    }
    line(out, 1, "}");
    line(out, 0, "}");
  }

  private static String generateCSharp(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("using System;\nusing System.Collections.Generic;\n\n");
    appendCSharpClass(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String csharpType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendCSharpClass(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "List<" + csharpType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "string";
      case NUMBER:
        return "double";
      case BOOLEAN:
        return "bool";
      case NULL:
      case UNKNOWN:
      default:
        return "object";
    }
  }

  private static void appendCSharpClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "public class " + className);
    line(out, 0, "{");
    for (FieldSpec field : fields) {
      String type = csharpType(field.node, className + pascal(field.jsonName), names, definitions);
      line(out, 1, "public " + type + " " + pascal(field.name) + " { get; set; }");
    }
    line(out, 0, "}");
  }

  private static String generateCpp(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("#include <any>\n#include <string>\n#include <vector>\n\n");
    appendCppStruct(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String cppType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendCppStruct(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "std::vector<" + cppType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "std::string";
      case NUMBER:
        return "double";
      case BOOLEAN:
        return "bool";
      case NULL:
      case UNKNOWN:
      default:
        return "std::any";
    }
  }

  private static void appendCppStruct(
      StringBuilder out,
      String structName,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "struct " + structName);
    line(out, 0, "{");
    for (FieldSpec field : fields) {
      String type = cppType(field.node, structName + pascal(field.jsonName), names, definitions);
      line(out, 1, type + " " + field.name + ";");
    }
    line(out, 0, "};");
  }

  private static String generateGo(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("package main\n\n");
    appendGoStruct(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String goType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendGoStruct(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "[]" + goType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions);
      case STRING:
        return "string";
      case NUMBER:
        return "float64";
      case BOOLEAN:
        return "bool";
      case NULL:
      case UNKNOWN:
      default:
        return "interface{}";
    }
  }

  private static void appendGoStruct(
      StringBuilder out,
      String structName,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "type " + structName + " struct {");
    for (FieldSpec field : fields) {
      String type = goType(field.node, structName + pascal(field.jsonName), names, definitions);
      line(out, 1, pascal(field.name) + " " + type + " `json:" + quoteGo(field.jsonName) + "`");
    }
    line(out, 0, "}");
  }

  private static String generateRust(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("use serde::{Deserialize, Serialize};\nuse serde_json::Value;\n\n");
    appendRustStruct(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String rustType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendRustStruct(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "Vec<" + rustType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "String";
      case NUMBER:
        return "f64";
      case BOOLEAN:
        return "bool";
      case NULL:
        return "Option<Value>";
      case UNKNOWN:
      default:
        return "Value";
    }
  }

  private static void appendRustStruct(
      StringBuilder out,
      String structName,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "#[derive(Debug, Clone, Serialize, Deserialize)]");
    line(out, 0, "pub struct " + structName + " {");
    for (FieldSpec field : fields) {
      String type = rustType(field.node, structName + pascal(field.jsonName), names, definitions);
      line(out, 1, "#[serde(rename = " + quoteRust(field.jsonName) + ")]");
      line(out, 1, "pub " + field.name + ": " + type + ",");
    }
    line(out, 0, "}");
  }

  private static String generateSwift(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder("import Foundation\n\n");
    appendSwiftStruct(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String swiftType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendSwiftStruct(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "[" + swiftType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + "]";
      case STRING:
        return "String";
      case NUMBER:
        return "Double";
      case BOOLEAN:
        return "Bool";
      case NULL:
        return "String?";
      case UNKNOWN:
      default:
        return "Any";
    }
  }

  private static void appendSwiftStruct(
      StringBuilder out,
      String structName,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "struct " + structName + ": Codable {");
    for (FieldSpec field : fields) {
      String type = swiftType(field.node, structName + pascal(field.jsonName), names, definitions);
      line(out, 1, "var " + field.name + ": " + type);
    }
    line(out, 0, "}");
  }

  private static String generateDart(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder();
    appendDartClass(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String dartType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendDartClass(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "List<" + dartType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + ">";
      case STRING:
        return "String";
      case NUMBER:
        return "num";
      case BOOLEAN:
        return "bool";
      case NULL:
      case UNKNOWN:
      default:
        return "dynamic";
    }
  }

  private static void appendDartClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "class " + className + " {");
    for (FieldSpec field : fields) {
      line(out, 1, "final " + dartType(field.node, className + pascal(field.jsonName), names, definitions) + " " + field.name + ";");
    }
    if (!fields.isEmpty()) {
      line(out, 1, className + "({");
      for (int i = 0; i < fields.size(); i++) {
        FieldSpec field = fields.get(i);
        String type = dartType(field.node, className + pascal(field.jsonName), names, definitions);
        line(out, 2, "required this." + field.name + (i + 1 == fields.size() ? "" : ","));
      }
      line(out, 1, "});");
    } else {
      line(out, 1, className + "();");
    }
    line(out, 0, "}");
  }

  private static String generateRuby(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, "class " + className);
    for (FieldSpec field : fields) {
      line(out, 1, "attr_accessor :" + field.name);
    }
    line(out, 1, "def initialize(data = {})");
    for (FieldSpec field : fields) {
      line(out, 2, "@" + field.name + " = data[" + quote(field.jsonName) + "]");
    }
    line(out, 1, "end");
    line(out, 0, "end");
    return out.toString();
  }

  private static String generateScala(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder();
    appendScalaCaseClass(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String scalaType(Node node, String base, Names names, StringBuilder definitions) {
    switch (node.kind) {
      case OBJECT:
        String name = names.take(base);
        appendScalaCaseClass(definitions, name, node, names, definitions);
        return name;
      case ARRAY:
        return "Seq[" + scalaType(node.item == null ? new Node(Kind.UNKNOWN) : node.item, base + "Item", names, definitions) + "]";
      case STRING:
        return "String";
      case NUMBER:
        return "Double";
      case BOOLEAN:
        return "Boolean";
      case NULL:
        return "Option[Any]";
      case UNKNOWN:
      default:
        return "Any";
    }
  }

  private static void appendScalaCaseClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    StringBuilder parameters = new StringBuilder();
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      if (i > 0) {
        parameters.append(", ");
      }
      parameters.append(field.name).append(": ").append(scalaType(field.node, className + pascal(field.jsonName), names, definitions));
    }
    line(out, 0, "case class " + className + "(" + parameters + ")");
  }

  private static String generateGroovy(String className, Node root) {
    Names names = new Names();
    names.take(className);
    StringBuilder definitions = new StringBuilder();
    StringBuilder out = new StringBuilder();
    appendGroovyClass(out, className, root, names, definitions);
    out.append(definitions);
    return out.toString();
  }

  private static String groovyType(Node node, String base, Names names, StringBuilder definitions) {
    if (node.kind == Kind.OBJECT) {
      String name = names.take(base);
      appendGroovyClass(definitions, name, node, names, definitions);
      return name;
    }
    if (node.kind == Kind.ARRAY) {
      return "List";
    }
    if (node.kind == Kind.STRING) {
      return "String";
    }
    if (node.kind == Kind.NUMBER) {
      return "Number";
    }
    if (node.kind == Kind.BOOLEAN) {
      return "boolean";
    }
    return "Object";
  }

  private static void appendGroovyClass(
      StringBuilder out,
      String className,
      Node node,
      Names names,
      StringBuilder definitions) {
    List<FieldSpec> fields = fieldsFor(node);
    line(out, 0, "class " + className + " {");
    for (FieldSpec field : fields) {
      line(out, 1, groovyType(field.node, className + pascal(field.jsonName), names, definitions) + " " + field.name);
    }
    line(out, 0, "}");
  }

  private static String generatePerl(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, "package " + className + ";");
    line(out, 0, "use strict;");
    line(out, 0, "use warnings;");
    line(out, 0, "sub new {");
    line(out, 1, "my ($class, %args) = @_;");
    line(out, 1, "my $self = bless {}, $class;");
    for (FieldSpec field : fields) {
      line(out, 1, "$self->{" + quote(field.jsonName) + "} = $args{" + quote(field.jsonName) + "};");
    }
    line(out, 1, "return $self;");
    line(out, 0, "}");
    line(out, 0, "1;");
    return out.toString();
  }

  private static String generateLua(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, "local " + className + " = {}");
    line(out, 0, className + ".__index = " + className);
    line(out, 0, "function " + className + ".new(data)");
    line(out, 1, "data = data or {}");
    line(out, 1, "local self = setmetatable({}, " + className + ")");
    for (FieldSpec field : fields) {
      line(out, 1, "self." + field.name + " = data[" + quote(field.jsonName) + "]");
    }
    line(out, 1, "return self");
    line(out, 0, "end");
    line(out, 0, "return " + className);
    return out.toString();
  }

  private static String generateR(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, className + " <- function(");
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      line(out, 1, field.name + " = NULL" + (i + 1 == fields.size() ? "" : ","));
    }
    line(out, 0, ") {");
    line(out, 1, "list(");
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      line(out, 2, field.name + " = " + field.name + (i + 1 == fields.size() ? "" : ","));
    }
    line(out, 1, ")");
    line(out, 0, "}");
    return out.toString();
  }

  private static String generateShell(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    String functionName = snake(className) + "_to_env";
    StringBuilder out = new StringBuilder("#!/usr/bin/env bash\n\n");
    line(out, 0, functionName + "() {");
    line(out, 1, "local json=\"$1\"");
    for (FieldSpec field : fields) {
      String variable = upperSnake(field.jsonName);
      String path = jqPath(field.jsonName);
      line(out, 1, variable + "=\"$(printf '%s' \"$json\" | jq -r '" + path + "')\"");
      line(out, 1, "export " + variable);
    }
    line(out, 0, "}");
    return out.toString();
  }

  private static String generateSql(String className, Node root) {
    List<FieldSpec> fields = fieldsFor(root);
    StringBuilder out = new StringBuilder();
    line(out, 0, "CREATE TABLE " + snake(className) + " (");
    for (int i = 0; i < fields.size(); i++) {
      FieldSpec field = fields.get(i);
      line(out, 1, snake(field.jsonName) + " " + sqlType(field.node) + (i + 1 == fields.size() ? "" : ","));
    }
    if (fields.isEmpty()) {
      line(out, 1, "value TEXT");
    }
    line(out, 0, ");");
    return out.toString();
  }

  private static String sqlType(Node node) {
    switch (node.kind) {
      case NUMBER:
        return "NUMERIC";
      case BOOLEAN:
        return "BOOLEAN";
      default:
        return "TEXT";
    }
  }

  private static String jqPath(String key) {
    if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return "." + key;
    }
    return ".[\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
  }

  private static String identifier(String raw, boolean upper) {
    String value = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_]", "_");
    if (value.isEmpty()) {
      value = "value";
    }
    if (Character.isDigit(value.charAt(0))) {
      value = "_" + value;
    }
    if (RESERVED.contains(value.toLowerCase(Locale.ROOT))) {
      value += "_";
    }
    if (upper) {
      return pascal(value);
    }
    return camel(value);
  }

  private static String pascal(String raw) {
    String value = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]+", " ").trim();
    if (value.isEmpty()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (String word : value.split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      out.append(Character.toUpperCase(word.charAt(0)));
      if (word.length() > 1) {
        out.append(word.substring(1));
      }
    }
    return out.toString();
  }

  private static String camel(String raw) {
    String value = pascal(raw);
    if (value.isEmpty()) {
      return "value";
    }
    return Character.toLowerCase(value.charAt(0)) + value.substring(1);
  }

  private static String snake(String raw) {
    String value = raw == null ? "" : raw.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
    if (value.isEmpty()) {
      return "generated_model";
    }
    if (Character.isDigit(value.charAt(0))) {
      value = "_" + value;
    }
    return value;
  }

  private static String upperSnake(String raw) {
    return snake(raw).toUpperCase(Locale.ROOT);
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String quotePhp(String value) {
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  private static String quoteGo(String value) {
    return quote(value);
  }

  private static String quoteRust(String value) {
    return quote(value);
  }

  private static void line(StringBuilder out, int level, String value) {
    out.append("  ".repeat(Math.max(0, level)));
    out.append(value);
    out.append('\n');
  }
}

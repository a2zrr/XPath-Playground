# XPath Playground

A lightweight, local, single-user web application to learn and experiment with XPath expressions against XML and XSD schemas.

## Features

- **3-Step Validation**:
  1. **XML Well-Formedness**: Verifies DOM syntax with built-in XXE protection (DOCTYPE declarations disabled).
  2. **XSD Validation**: Checks schema correctness and reports all errors.
  3. **XML vs. XSD Schema Validation**: Validates XML against the provided XSD with line and column error reporting.
- **Smart Namespace Mapping**: Automatically discovers XML namespaces and binds the default namespace to prefix `d` (or `d1`, `d2`...) so XPath expressions like `//d:book` work out of the box.
- **Rich XPath Evaluation**:
  - Supports `NODESET`, `NODE`, `STRING`, `NUMBER`, and `BOOLEAN` result types.
  - Formats elements as indented XML and attributes/scalars as values.
  - Safe error handling (syntax errors displayed cleanly in red without server crashes).
- **Stateless & Zero Persistence**: Everything runs locally in memory; no database or external cloud dependencies.

## Tech Stack

- **Java 25+** (Standard JDK XML/XSD/XPath built-ins only)
- **Maven**
- **Javalin 6.x** (Embedded Jetty + Jackson)
- **Vanilla HTML / CSS / JS** (Single static file, no build tools, no frameworks)

## Prerequisites

- **Java JDK 25** (or compatible modern JDK)
- **Apache Maven 3.9+**

## Quick Start

1. Clone or download the repository:
   ```bash
   git clone https://github.com/a2zrr/XPath-Playground.git
   cd xpath-playground
   ```

2. Compile and run the server:
   ```bash
   mvn -q compile exec:java
   ```

3. Open your browser and navigate to:
   ```
   http://localhost:7070
   ```

## Usage

1. **Select an XSD Schema** and an **XML Document** (you can use the ready-made test files in the [`samples/`](file:///C:/Users/Dell/Desktop/XPath%20Playground/samples) folder).
2. Once both files are loaded, validation runs automatically.
3. If all checks pass (`✓`), the XPath input unlocks.
4. Check the **Namespaces** panel to see mapped prefixes (e.g. `d` for default namespaces).
5. Enter an XPath expression (e.g. `//d:book/d:title` or `count(//d:book)`) and press **Enter** or click **Run**.

## Samples

The [`samples/`](file:///C:/Users/Dell/Desktop/XPath%20Playground/samples) directory includes test cases for quick experimentation:
- `ok.xsd` & `ok.xml`: Valid book catalog schema and document with default namespaces.
- `bad-wellformed.xml`: XML with syntax error (unclosed tag).
- `bad-schema.xsd`: Invalid XML Schema definition.
- `invalid.xml`: Well-formed XML that violates schema type/presence constraints.

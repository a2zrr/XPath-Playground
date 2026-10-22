# Project: XPath Playground (local web app, Java + HTML)

## Goal
A local, single-user web app to learn XPath. The user loads an XSD file and an XML
file; the app validates them in three steps; if all pass, the user types XPath
expressions and sees the results. No login, no database, no persistence, and no
features beyond this spec.

## Tech constraints
- Java 25.0.4.1, Maven 3.10.0.
- Web server: Javalin 6.x via the single dependency io.javalin:javalin-bundle
  (latest stable 6.x; if a version isn't found, use the previous one).
  Bind to 127.0.0.1, port 7070.
- XML/XSD/XPath: JDK built-ins only (javax.xml.parsers, javax.xml.validation,
  javax.xml.xpath, javax.xml.transform). No other XML library.
- Frontend: ONE static file src/main/resources/public/index.html. Vanilla
  HTML/CSS/JS, no framework, no CDN, no build step.
- Run with: mvn -q compile exec:java (configure exec-maven-plugin in pom.xml,
  main class app.Main). http://localhost:7070 must show the page.
- Keep it small: package app, at most 4 Java files (Main, ValidationService,
  XPathService, plus one for shared records/namespace helper if needed).
- Add a .gitignore containing target/.

## Behavior
Stateless server. The browser reads the files with file.text() and sends their
contents as JSON strings. The server keeps no state.

### POST /api/validate   body: {"xml": "...", "xsd": "..."}
Response:
{ "xmlWellFormed": {status, errors}, "xsdValid": {status, errors},
  "xmlMatchesXsd": {status, errors}, "allValid": bool,
  "namespaces": {prefix: uri} }
status is "ok" | "error" | "skipped". errors is a list of {line, column, message}.
Steps:
1. XML well-formed: parse with a namespace-aware DocumentBuilder. Disable DOCTYPE
   declarations (XXE protection) and report that as a normal error.
2. XSD valid: SchemaFactory for W3C XML Schema, newSchema(StreamSource) with an
   ErrorHandler that collects ALL errors, not just the first.
3. XML matches XSD: Validator with an ErrorHandler collecting all errors with
   line/column. Run only if steps 1 and 2 are ok, otherwise status "skipped".
Namespaces: if step 1 is ok, return every xmlns declaration found in the document.
The default namespace (no prefix) is mapped to the prefix "d" (if taken: d1, d2...).

### POST /api/xpath   body: {"xml": "...", "expression": "..."}
- Parse the XML (namespace-aware) and evaluate the expression with a
  NamespaceContext built from the same namespace map as above, so //d:book works
  when the XML uses a default namespace.
- Use evaluateExpression(expr, doc, XPathEvaluationResult.class) (Java 9+) to
  detect the result type: NODESET, NODE, STRING, NUMBER, BOOLEAN.
- Response: {type, count, truncated, items: [...]}.
  Node items: {kind (element|attribute|text|comment...), name, value
  (string-value), xml (serialized element, indented, no XML declaration,
  elements only)}. Scalar types: one item with the value.
  Cap at 500 items and set truncated=true.
- XPath syntax/evaluation errors: HTTP 200 with {error: message}, never a 500.
- Malformed requests: HTTP 400 with {error}.

## UI (index.html)
- Two file inputs: XSD and XML. Validation runs automatically once both are
  selected, and again when either changes.
- Status panel: three rows (XML well-formed, XSD valid, XML valid against XSD),
  each with a ✓ / ✗ / – icon; errors listed underneath as line:column message.
- Namespaces panel: prefix -> URI table with the note "use these prefixes in XPath".
- XPath section: disabled until allValid is true. Text input + Run button
  (Enter also runs).
- Results: type badge + count, then the list. Elements as preformatted XML,
  attributes/text as name=value or the text, scalars as the value, errors in red.
- Minimal clean CSS. Nothing more.

## Samples (create in samples/)
- ok.xsd + ok.xml: small library of books (title, author, @year, price), with a
  targetNamespace and a default xmlns in the XML so namespace handling is exercised.
- bad-wellformed.xml (unclosed tag)
- bad-schema.xsd (invalid schema)
- invalid.xml (well-formed but violates ok.xsd: missing required element, wrong type)

## Working rules (token economy)
- Do not ask questions. Make reasonable decisions and list assumptions in one line
  at the end.
- Do not explore or scaffold anything not listed. No test framework, no README,
  no Docker, no extra features.
- Write each file once, complete. Do not rewrite files to polish them.
- Run Maven with -q -B and never paste long build logs.
- Verify ONCE: start the server, curl /api/validate with ok, bad-wellformed,
  bad-schema and invalid samples, then curl /api/xpath with
  //d:book/d:title and count(//d:book). Then stop the server.
- Final reply: at most 10 lines (how to run, files created, anything unverified).
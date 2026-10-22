package app;

import app.Models.StepResult;
import app.Models.ValidationError;
import app.Models.ValidationResponse;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.*;

public class ValidationService {

    public static class ParseResult {
        public final StepResult stepResult;
        public final Document document;

        public ParseResult(StepResult stepResult, Document document) {
            this.stepResult = stepResult;
            this.document = document;
        }
    }

    public static ParseResult checkXmlWellFormed(String xml) {
        if (xml == null || xml.isBlank()) {
            return new ParseResult(new StepResult("error", List.of(new ValidationError(0, 0, "XML content is empty"))), null);
        }

        List<ValidationError> errors = new ArrayList<>();
        Document document = null;

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            // Disable DOCTYPE declarations (XXE protection)
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {}

                @Override
                public void error(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }
            });

            document = db.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }
        } catch (Exception e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(-1, -1, e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }

        if (errors.isEmpty() && document != null) {
            return new ParseResult(new StepResult("ok", List.of()), document);
        } else {
            return new ParseResult(new StepResult("error", errors), null);
        }
    }

    public static class SchemaResult {
        public final StepResult stepResult;
        public final Schema schema;

        public SchemaResult(StepResult stepResult, Schema schema) {
            this.stepResult = stepResult;
            this.schema = schema;
        }
    }

    public static SchemaResult checkXsdValid(String xsd) {
        if (xsd == null || xsd.isBlank()) {
            return new SchemaResult(new StepResult("error", List.of(new ValidationError(0, 0, "XSD content is empty"))), null);
        }

        List<ValidationError> errors = new ArrayList<>();
        Schema schema = null;

        try {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            sf.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {}

                @Override
                public void error(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }
            });

            schema = sf.newSchema(new StreamSource(new StringReader(xsd)));
        } catch (SAXParseException e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }
        } catch (Exception e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(-1, -1, e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }

        if (errors.isEmpty() && schema != null) {
            return new SchemaResult(new StepResult("ok", List.of()), schema);
        } else {
            return new SchemaResult(new StepResult("error", errors), null);
        }
    }

    public static StepResult checkXmlMatchesXsd(Schema schema, String xml) {
        List<ValidationError> errors = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {}

                @Override
                public void error(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }
            });

            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
            }
        } catch (Exception e) {
            if (errors.isEmpty()) {
                errors.add(new ValidationError(-1, -1, e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }

        if (errors.isEmpty()) {
            return new StepResult("ok", List.of());
        } else {
            return new StepResult("error", errors);
        }
    }

    public static Map<String, String> extractNamespaces(Document doc) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        if (doc == null || doc.getDocumentElement() == null) {
            return namespaces;
        }

        Map<String, String> explicitNamespaces = new LinkedHashMap<>();
        List<String> defaultNamespaces = new ArrayList<>();

        Deque<Node> stack = new ArrayDeque<>();
        stack.push(doc.getDocumentElement());

        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                NamedNodeMap attrs = node.getAttributes();
                if (attrs != null) {
                    for (int i = 0; i < attrs.getLength(); i++) {
                        Node attr = attrs.item(i);
                        String name = attr.getNodeName();
                        String value = attr.getNodeValue();
                        if ("xmlns".equals(name)) {
                            if (value != null && !value.isBlank() && !defaultNamespaces.contains(value)) {
                                defaultNamespaces.add(value);
                            }
                        } else if (name.startsWith("xmlns:")) {
                            String prefix = name.substring(6);
                            if (value != null && !value.isBlank()) {
                                explicitNamespaces.putIfAbsent(prefix, value);
                            }
                        }
                    }
                }

                NodeList children = node.getChildNodes();
                for (int i = children.getLength() - 1; i >= 0; i--) {
                    stack.push(children.item(i));
                }
            }
        }

        namespaces.putAll(explicitNamespaces);

        for (String defUri : defaultNamespaces) {
            String prefix = "d";
            if (namespaces.containsKey(prefix) && !namespaces.get(prefix).equals(defUri)) {
                int index = 1;
                while (namespaces.containsKey("d" + index) && !namespaces.get("d" + index).equals(defUri)) {
                    index++;
                }
                prefix = "d" + index;
            }
            namespaces.put(prefix, defUri);
        }

        return namespaces;
    }

    public static ValidationResponse validate(String xml, String xsd) {
        ParseResult xmlResult = checkXmlWellFormed(xml);
        SchemaResult xsdResult = checkXsdValid(xsd);

        StepResult xmlMatchesXsd;
        Map<String, String> namespaces = Collections.emptyMap();

        if ("ok".equals(xmlResult.stepResult.status())) {
            namespaces = extractNamespaces(xmlResult.document);
        }

        if ("ok".equals(xmlResult.stepResult.status()) && "ok".equals(xsdResult.stepResult.status())) {
            xmlMatchesXsd = checkXmlMatchesXsd(xsdResult.schema, xml);
        } else {
            xmlMatchesXsd = new StepResult("skipped", List.of());
        }

        boolean allValid = "ok".equals(xmlResult.stepResult.status())
                && "ok".equals(xsdResult.stepResult.status())
                && "ok".equals(xmlMatchesXsd.status());

        return new ValidationResponse(
                xmlResult.stepResult,
                xsdResult.stepResult,
                xmlMatchesXsd,
                allValid,
                namespaces
        );
    }
}

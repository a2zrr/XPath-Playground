package app;

import app.Models.XPathResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathEvaluationResult;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathNodes;
import java.io.StringWriter;
import java.util.*;

public class XPathService {

    public static class MapNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixToUri;
        private final Map<String, List<String>> uriToPrefixes = new HashMap<>();

        public MapNamespaceContext(Map<String, String> map) {
            this.prefixToUri = new HashMap<>(map);
            for (Map.Entry<String, String> entry : map.entrySet()) {
                uriToPrefixes.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) throw new IllegalArgumentException("Prefix cannot be null");
            if (XMLConstants.XML_NS_PREFIX.equals(prefix)) return XMLConstants.XML_NS_URI;
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            return prefixToUri.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            if (namespaceURI == null) throw new IllegalArgumentException("Namespace URI cannot be null");
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) return XMLConstants.XML_NS_PREFIX;
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) return XMLConstants.XMLNS_ATTRIBUTE;
            List<String> prefixes = uriToPrefixes.get(namespaceURI);
            return (prefixes != null && !prefixes.isEmpty()) ? prefixes.get(0) : null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            if (namespaceURI == null) throw new IllegalArgumentException("Namespace URI cannot be null");
            if (XMLConstants.XML_NS_URI.equals(namespaceURI)) return List.of(XMLConstants.XML_NS_PREFIX).iterator();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespaceURI)) return List.of(XMLConstants.XMLNS_ATTRIBUTE).iterator();
            List<String> prefixes = uriToPrefixes.get(namespaceURI);
            return (prefixes != null) ? Collections.unmodifiableList(prefixes).iterator() : Collections.emptyIterator();
        }
    }

    public static XPathResponse evaluate(String xml, String expression) throws Exception {
        ValidationService.ParseResult parseResult = ValidationService.checkXmlWellFormed(xml);
        if (!"ok".equals(parseResult.stepResult.status())) {
            String errorMsg = parseResult.stepResult.errors().isEmpty()
                    ? "XML is not well-formed"
                    : parseResult.stepResult.errors().get(0).message();
            throw new IllegalArgumentException("XML well-formed error: " + errorMsg);
        }

        Document doc = parseResult.document;
        Map<String, String> namespaces = ValidationService.extractNamespaces(doc);

        XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        xpath.setNamespaceContext(new MapNamespaceContext(namespaces));

        XPathEvaluationResult<?> result = xpath.evaluateExpression(expression, doc, XPathEvaluationResult.class);
        XPathEvaluationResult.XPathResultType type = result.type();

        return switch (type) {
            case NODESET -> {
                XPathNodes nodes = (XPathNodes) result.value();
                int totalCount = nodes.size();
                boolean truncated = totalCount > 500;
                int limit = Math.min(totalCount, 500);
                List<Map<String, Object>> items = new ArrayList<>(limit);
                for (int i = 0; i < limit; i++) {
                    items.add(serializeNode(nodes.get(i)));
                }
                yield new XPathResponse("NODESET", totalCount, truncated, items);
            }
            case NODE -> {
                Node node = (Node) result.value();
                List<Map<String, Object>> items = (node != null) ? List.of(serializeNode(node)) : List.of();
                yield new XPathResponse("NODE", items.size(), false, items);
            }
            case STRING -> {
                String s = (String) result.value();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", s);
                yield new XPathResponse("STRING", 1, false, List.of(item));
            }
            case NUMBER -> {
                Number n = (Number) result.value();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", n);
                yield new XPathResponse("NUMBER", 1, false, List.of(item));
            }
            case BOOLEAN -> {
                Boolean b = (Boolean) result.value();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("value", b);
                yield new XPathResponse("BOOLEAN", 1, false, List.of(item));
            }
            case ANY -> {
                Object val = result.value();
                if (val instanceof XPathNodes nodes) {
                    int totalCount = nodes.size();
                    boolean truncated = totalCount > 500;
                    int limit = Math.min(totalCount, 500);
                    List<Map<String, Object>> items = new ArrayList<>(limit);
                    for (int i = 0; i < limit; i++) {
                        items.add(serializeNode(nodes.get(i)));
                    }
                    yield new XPathResponse("NODESET", totalCount, truncated, items);
                } else if (val instanceof Node node) {
                    yield new XPathResponse("NODE", 1, false, List.of(serializeNode(node)));
                } else if (val instanceof Number n) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("value", n);
                    yield new XPathResponse("NUMBER", 1, false, List.of(item));
                } else if (val instanceof Boolean b) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("value", b);
                    yield new XPathResponse("BOOLEAN", 1, false, List.of(item));
                } else {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("value", val != null ? val.toString() : "");
                    yield new XPathResponse("STRING", 1, false, List.of(item));
                }
            }
        };
    }

    private static Map<String, Object> serializeNode(Node node) {
        Map<String, Object> map = new LinkedHashMap<>();
        String kind = switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> "element";
            case Node.ATTRIBUTE_NODE -> "attribute";
            case Node.TEXT_NODE -> "text";
            case Node.CDATA_SECTION_NODE -> "cdata";
            case Node.COMMENT_NODE -> "comment";
            case Node.DOCUMENT_NODE -> "document";
            case Node.PROCESSING_INSTRUCTION_NODE -> "processing-instruction";
            default -> "other";
        };
        map.put("kind", kind);
        map.put("name", node.getNodeName());
        map.put("value", node.getTextContent());
        map.put("path", getNodePath(node));

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            map.put("xml", serializeElement((Element) node));
        } else {
            map.put("xml", null);
        }
        return map;
    }

    private static String getNodePath(Node node) {
        if (node == null) return "";
        if (node.getNodeType() == Node.DOCUMENT_NODE) return "/";

        if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
            org.w3c.dom.Attr attr = (org.w3c.dom.Attr) node;
            Element owner = attr.getOwnerElement();
            String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
            if (attrName.contains(":")) {
                attrName = attrName.substring(attrName.indexOf(':') + 1);
            }
            String parentPath = owner != null ? getNodePath(owner) : "";
            return (parentPath.equals("/") ? "" : parentPath) + "/@" + attrName;
        }

        String segment = "";
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE -> {
                int index = 1;
                Node prev = node.getPreviousSibling();
                while (prev != null) {
                    if (prev.getNodeType() == Node.ELEMENT_NODE) {
                        index++;
                    }
                    prev = prev.getPreviousSibling();
                }
                segment = "/*[" + index + "]";
            }
            case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                int index = 1;
                Node prev = node.getPreviousSibling();
                while (prev != null) {
                    if (prev.getNodeType() == Node.TEXT_NODE || prev.getNodeType() == Node.CDATA_SECTION_NODE) {
                        index++;
                    }
                    prev = prev.getPreviousSibling();
                }
                segment = "/text()[" + index + "]";
            }
            case Node.COMMENT_NODE -> {
                int index = 1;
                Node prev = node.getPreviousSibling();
                while (prev != null) {
                    if (prev.getNodeType() == Node.COMMENT_NODE) {
                        index++;
                    }
                    prev = prev.getPreviousSibling();
                }
                segment = "/comment()[" + index + "]";
            }
            default -> {
                return "";
            }
        }

        Node parent = node.getParentNode();
        if (parent == null || parent.getNodeType() == Node.DOCUMENT_NODE) {
            return segment;
        }
        String parentPath = getNodePath(parent);
        return (parentPath.equals("/") ? "" : parentPath) + segment;
    }

    private static String serializeElement(Element element) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(sw));
            return sw.toString().trim();
        } catch (Exception e) {
            return "<" + element.getTagName() + ">" + element.getTextContent() + "</" + element.getTagName() + ">";
        }
    }
}

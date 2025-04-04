//package it.gov.pagopa.fdrxmltojson.util;
//
//import jakarta.xml.bind.JAXBContext;
//import jakarta.xml.bind.JAXBElement;
//import jakarta.xml.bind.JAXBException;
//import jakarta.xml.bind.Unmarshaller;
//import org.w3c.dom.Document;
//import org.w3c.dom.Element;
//import org.w3c.dom.Node;
//import org.w3c.dom.NodeList;
//import org.xml.sax.InputSource;
//
//import javax.xml.parsers.DocumentBuilder;
//import javax.xml.parsers.DocumentBuilderFactory;
//import javax.xml.parsers.ParserConfigurationException;
//import javax.xml.stream.XMLInputFactory;
//import javax.xml.stream.XMLStreamReader;
//import javax.xml.transform.Transformer;
//import javax.xml.transform.TransformerConfigurationException;
//import javax.xml.transform.TransformerException;
//import javax.xml.transform.TransformerFactory;
//import javax.xml.transform.dom.DOMSource;
//import javax.xml.transform.stream.StreamResult;
//import javax.xml.transform.stream.StreamSource;
//import javax.xml.xpath.XPath;
//import javax.xml.xpath.XPathConstants;
//import javax.xml.xpath.XPathExpression;
//import javax.xml.xpath.XPathFactory;
//import java.io.*;
//
//public class XMLUtil {
//
//    public static Document loadXML(InputStream inputStream) throws Exception{
//        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
//        DocumentBuilder db = dbf.newDocumentBuilder();
//        return db.parse(new InputSource(inputStream));
//    }
//
//    public static Element searchNodeByName(Document doc, String tagName) throws Exception {
//        XPathFactory xPathfactory = XPathFactory.newInstance();
//        XPath xpath = xPathfactory.newXPath();
//        XPathExpression expr = xpath.compile("//*[local-name()='" + tagName + "']");
//        return (Element) expr.evaluate(doc, XPathConstants.NODE);
//    }
//
//    public static Element searchNodeByName(Node node, String elementToSearch) {
//        NodeList nodeList = node.getChildNodes();
//        Element elementFound = null;
//        for (int i = 0; i < nodeList.getLength(); i++) {
//            Node currentNode = nodeList.item(i);
//            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
//                Element element = (Element) currentNode;
//                if(element.getTagName().endsWith(elementToSearch)){
//                    return element;
//                } else {
//                    elementFound = searchNodeByName(element, elementToSearch);
//                    if(elementFound!=null){
//                        return elementFound;
//                    }
//                }
//            }
//        }
//        return null;
//    }
//
//    public static <T> T getInstanceByNode(Node node, Class<T> type) throws JAXBException, IOException, ParserConfigurationException, TransformerException {
//        JAXBContext jaxbContext = JAXBContext.newInstance(type);
//        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//
//        JAXBElement<T> jaxb =  unmarshaller.unmarshal(node, type);
//        return jaxb.getValue();
//    }
//
//    private static void toFile(Node node) throws TransformerException, IOException, ParserConfigurationException {
//        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//        factory.setNamespaceAware(true);
//        DocumentBuilder builder = factory.newDocumentBuilder();
//        Document document = builder.newDocument();
//        Node importedNode = document.importNode(node, true);
//        document.appendChild(importedNode);
//
//        TransformerFactory transformerFactory = TransformerFactory.newInstance();
//        Transformer transformer = transformerFactory.newTransformer();
//        DOMSource source = new DOMSource(document);
//        File tempFile = File.createTempFile("extracted", ".xml");
//        BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
//        StreamResult result = new StreamResult(writer);
//        transformer.transform(source, result);
//    }
//
//    public static <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
//        JAXBContext jaxbContext = JAXBContext.newInstance(type);
//        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
//        JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
//        return jaxb.getValue();
//    }
//
//
//}

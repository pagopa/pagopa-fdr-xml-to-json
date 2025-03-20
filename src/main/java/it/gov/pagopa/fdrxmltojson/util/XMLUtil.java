package it.gov.pagopa.fdrxmltojson.util;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class XMLUtil {

    public static Document loadXML(InputStream inputStream) throws Exception{
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(inputStream));
    }

    public static Element searchNodeByName(Node node, String elementToSearch) {
        NodeList nodeList = node.getChildNodes();
        Element elementFound = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) currentNode;
                if(element.getTagName().endsWith(elementToSearch)){
                    return element;
                } else {
                    elementFound = searchNodeByName(element, elementToSearch);
                    if(elementFound!=null){
                        return elementFound;
                    }
                }
            }
        }
        return null;
    }

    public static <T> T getInstanceByNode(Node node, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<T> jaxb =  unmarshaller.unmarshal(node, type);
        return jaxb.getValue();
    }

    public static <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
        return jaxb.getValue();
    }

    public static String messageFormat(String sessionId, String invocationId, String pspId, String fileName, String message, Object... args) {
        String suffix = String.format(" [sessionId: %s][invocationId: %s][psp: %s][filename: %s]", sessionId, invocationId, pspId, fileName);
        return String.format(message, args) + suffix;
    }
}

package it.gov.pagopa.fdrxmltojson.util;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.stream.*;
import javax.xml.transform.stream.StreamSource;
import java.io.*;


public class XMLParser {

    private static final String NODO_INVIA_FLUSSO_RENDICONTAZIONE = "nodoInviaFlussoRendicontazione";

    public <T> T getInstanceByStAX(InputStream inputStream, Class<T> type) throws XMLStreamException, JAXBException {
        XMLInputFactory xif = XMLInputFactory.newFactory();
        xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false); // disable external entities as DTD
        xif.setProperty(XMLInputFactory.SUPPORT_DTD, false); // disable DTD to accelerate parsing
        xif.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true); // enable namespace support
        xif.setProperty(XMLInputFactory.IS_COALESCING, false); // avoid node concatenation
        xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false); // don't replace &lt; o &amp; with < (i.e. the XML must be valid to improve parsing performance)

        XMLStreamReader reader = xif.createXMLStreamReader(inputStream);

        // search NODO_INVIA_FLUSSO_RENDICONTAZIONE node
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT &&
                    NODO_INVIA_FLUSSO_RENDICONTAZIONE.equals(reader.getLocalName())) {
                break;
            }
        }

        // check namespace
        String namespaceURI = reader.getNamespaceURI();
        if (namespaceURI == null || namespaceURI.isEmpty()) {
            throw new IllegalStateException("Namespace not found for <" + NODO_INVIA_FLUSSO_RENDICONTAZIONE +">");
        }

        // JAXB Unmarshaller with namespace support
        JAXBContext jaxbContext = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

        JAXBElement<T> jaxbElement = unmarshaller.unmarshal(reader, type);

        reader.close();
        return jaxbElement.getValue();
    }

    public <T> T getInstanceByBytes(byte[] content, Class<T> type) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<T> jaxb =  unmarshaller.unmarshal(new StreamSource(new ByteArrayInputStream(content)), type);
        return jaxb.getValue();
    }

}

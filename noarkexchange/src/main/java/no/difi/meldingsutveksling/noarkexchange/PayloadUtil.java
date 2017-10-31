package no.difi.meldingsutveksling.noarkexchange;

import lombok.extern.slf4j.Slf4j;
import no.difi.meldingsutveksling.noarkexchange.schema.AppReceiptType;
import org.springframework.util.StringUtils;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Slf4j
public class PayloadUtil {
    public static final String APP_RECEIPT_INDICATOR = "AppReceipt";
    public static final String PAYLOAD_UNKNOWN_TYPE = "Payload is of unknown type cannot determine what type of message it is";

    public static boolean isAppReceipt(Object payload) {
        if (payload instanceof AppReceiptType) {
            return true;
        }
        if(payload instanceof String) {
            return ((String) payload).contains(APP_RECEIPT_INDICATOR);
        }
        if(payload instanceof Node) {
            Node firstChild = ((Node) payload).getFirstChild();
            if (firstChild != null) {
                final String nodeName = firstChild.getTextContent();
                return nodeName.contains(APP_RECEIPT_INDICATOR);
            }
        }
        return false;
    }

    public static String payloadAsString(Object payload) {
        if(payload instanceof String) {
            return ((String) payload);
        } else if (payload instanceof Node) {
            return ((Node) payload).getFirstChild().getTextContent();
        } else {
            throw new RuntimeException("Could not get payload as String");
        }
    }

    public static boolean isEmpty(Object payload) {
        if (payload instanceof String) {
            return StringUtils.isEmpty(payload);
        } else if (payload instanceof Node) {
           return  !((Node) payload).hasChildNodes();
        } else {
            throw new RuntimeException(PAYLOAD_UNKNOWN_TYPE);
        }
    }

    public static AppReceiptType getAppReceiptType(Object payload) throws JAXBException {
        final String payloadAsString = payloadAsString(payload);

        StringSource source = new StringSource(payloadAsString);
        JAXBContext jaxbContext = JAXBContext.newInstance("no.difi.meldingsutveksling.noarkexchange.schema");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<AppReceiptType> r = unmarshaller.unmarshal(source, AppReceiptType.class);
        return r.getValue();
    }

    public static String queryPayload(Object payload, String xpath) throws PayloadException {
        String result;

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        try {
            XPathExpression expression = xPath.compile(xpath);
            String doc;
            if (payload instanceof String) {
                doc = (String) payload;
            } else {
                doc = ((Node) payload).getFirstChild().getTextContent().trim();
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(doc.getBytes(java.nio.charset.Charset.forName("utf-8"))));
            result = expression.evaluate(document);
        } catch (XPathExpressionException | ParserConfigurationException | SAXException | IOException e) {
            throw new PayloadException("Could not execute query \'"+xpath+"\'  on the payload");
        }
        return result;
    }

    public static String queryJpId(Object payload) {
        try {
            return queryPayload(payload, "/Melding/journpost/jpId");
        } catch (PayloadException e) {
            log.warn("Could not read jpId from payload", e);
            return "";
        }
    }

}
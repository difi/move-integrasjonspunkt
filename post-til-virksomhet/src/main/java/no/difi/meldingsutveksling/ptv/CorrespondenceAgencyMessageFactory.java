package no.difi.meldingsutveksling.ptv;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import no.altinn.schemas.serviceengine.formsengine._2009._10.TransportType;
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.AttachmentsV2;
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.ExternalContentV2;
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.MyInsertCorrespondenceV2;
import no.altinn.schemas.services.serviceengine.correspondence._2010._10.ObjectFactory;
import no.altinn.schemas.services.serviceengine.correspondence._2014._10.CorrespondenceStatusFilterV2;
import no.altinn.schemas.services.serviceengine.notification._2009._10.*;
import no.altinn.schemas.services.serviceengine.subscription._2009._10.AttachmentFunctionType;
import no.altinn.services.serviceengine.correspondence._2009._10.GetCorrespondenceStatusDetailsV2;
import no.altinn.services.serviceengine.correspondence._2009._10.InsertCorrespondenceV2;
import no.altinn.services.serviceengine.reporteeelementlist._2010._10.BinaryAttachmentExternalBEV2List;
import no.altinn.services.serviceengine.reporteeelementlist._2010._10.BinaryAttachmentV2;
import no.difi.meldingsutveksling.core.EDUCore;
import no.difi.meldingsutveksling.receipt.Conversation;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Class used to create an InsertCorrespondenceV2 object based on an internal message format.
 */
public class CorrespondenceAgencyMessageFactory {

    private static final Map<Integer, String> serviceEditionMapping= new HashMap<>();

    static {
        serviceEditionMapping.put(1, "Plan, bygg og geodata");
        serviceEditionMapping.put(2, "Helse, sosial og omsorg");
        serviceEditionMapping.put(3, "Oppvekst og utdanning");
        serviceEditionMapping.put(4, "Kultur, idrett og fritid");
        serviceEditionMapping.put(5, "Trafikk, reiser og samferdsel");
        serviceEditionMapping.put(6, "Natur og miljø");
        serviceEditionMapping.put(7, "Næringsutvikling");
        serviceEditionMapping.put(8, "Skatter og avgifter");
        serviceEditionMapping.put(9, "Tekniske tjenester");
        serviceEditionMapping.put(10, "Administrasjon");
    }

    private CorrespondenceAgencyMessageFactory() {
    }

    public static InsertCorrespondenceV2 create(CorrespondenceAgencyConfiguration postConfig, EDUCore edu) {

        MyInsertCorrespondenceV2 correspondence = new MyInsertCorrespondenceV2();
        ObjectFactory objectFactory = new ObjectFactory();

        correspondence.setReportee(objectFactory.createMyInsertCorrespondenceV2Reportee(edu.getReceiver().getIdentifier()));
        // Service code, default 4255
        correspondence.setServiceCode(getServiceCode(postConfig));
        // Service edition, default 10
        correspondence.setServiceEdition(getServiceEditionCode(postConfig));
        // Should the user be allowed to forward the message from portal
        correspondence.setAllowForwarding(objectFactory.createMyInsertCorrespondenceV2AllowForwarding(false));
        // Name of the message sender, always "Avsender"
        correspondence.setMessageSender(objectFactory.createMyInsertCorrespondenceV2MessageSender("Avsender"));
        // The date and time the message should be visible in the Portal
        correspondence.setVisibleDateTime(toXmlGregorianCalendar(ZonedDateTime.now()));
        correspondence.setDueDateTime(toXmlGregorianCalendar(ZonedDateTime.now().plusDays(7)));

        ExternalContentV2 externalContentV2 = new ExternalContentV2();
        externalContentV2.setLanguageCode(objectFactory.createExternalContentV2LanguageCode("1044"));
        externalContentV2.setMessageTitle(objectFactory.createExternalContentV2MessageTitle(edu.getPayloadAsMeldingType().getJournpost().getJpInnhold()));
        externalContentV2.setMessageSummary(objectFactory.createExternalContentV2MessageSummary(edu.getPayloadAsMeldingType().getJournpost().getJpInnhold()));
        externalContentV2.setMessageBody(objectFactory.createExternalContentV2MessageBody(edu.getPayloadAsMeldingType().getJournpost().getJpOffinnhold()));

        // The date and time the message can be deleted by the user
        correspondence.setAllowSystemDeleteDateTime(
                objectFactory.createMyInsertCorrespondenceV2AllowSystemDeleteDateTime(
                        toXmlGregorianCalendar(getAllowSystemDeleteDateTime(edu))));

        // FunctionType
        no.altinn.services.serviceengine.reporteeelementlist._2010._10.ObjectFactory reporteeFactory = new no.altinn.services.serviceengine.reporteeelementlist._2010._10.ObjectFactory();
        BinaryAttachmentExternalBEV2List attachmentExternalBEV2List = new BinaryAttachmentExternalBEV2List();
        edu.getPayloadAsMeldingType().getJournpost().getDokument().forEach(d -> {
            BinaryAttachmentV2 binaryAttachmentV2 = new BinaryAttachmentV2();
            binaryAttachmentV2.setFunctionType(AttachmentFunctionType.fromValue("Unspecified"));
            binaryAttachmentV2.setFileName(reporteeFactory.createBinaryAttachmentV2FileName(d.getVeFilnavn()));
            binaryAttachmentV2.setName(reporteeFactory.createBinaryAttachmentV2Name(d.getVeFilnavn()));
            binaryAttachmentV2.setEncrypted(false);
            binaryAttachmentV2.setSendersReference(reporteeFactory.createBinaryAttachmentV2SendersReference("AttachmentReference_as123452"));
            binaryAttachmentV2.setData(reporteeFactory.createBinaryAttachmentV2Data(d.getFil().getBase64()));
            attachmentExternalBEV2List.getBinaryAttachmentV2().add(binaryAttachmentV2);
        });

        AttachmentsV2 attachmentsV2 = new AttachmentsV2();
        attachmentsV2.setBinaryAttachments(objectFactory.createAttachmentsV2BinaryAttachments(attachmentExternalBEV2List));
        externalContentV2.setAttachments(objectFactory.createExternalContentV2Attachments(attachmentsV2));
        correspondence.setContent(objectFactory.createMyInsertCorrespondenceV2Content(externalContentV2));

        List<Notification2009> notificationList = createNotifications(postConfig, edu);

        NotificationBEList notifications = new NotificationBEList();
        List<Notification2009> notification = notifications.getNotification();
        notificationList.forEach(notification::add);
        correspondence.setNotifications(objectFactory.createMyInsertCorrespondenceV2Notifications(notifications));

        no.altinn.services.serviceengine.correspondence._2009._10.ObjectFactory correspondenceObjectFactory = new no.altinn.services.serviceengine.correspondence._2009._10.ObjectFactory();
        final InsertCorrespondenceV2 myInsertCorrespondenceV2 = correspondenceObjectFactory.createInsertCorrespondenceV2();
        myInsertCorrespondenceV2.setCorrespondence(correspondence);
        myInsertCorrespondenceV2.setSystemUserCode(postConfig.getSystemUserCode());
        // Reference set by the message sender
        myInsertCorrespondenceV2.setExternalShipmentReference(edu.getId());

        return myInsertCorrespondenceV2;
    }

    private static List<Notification2009> createNotifications(CorrespondenceAgencyConfiguration config, EDUCore edu) {

        List<Notification2009> notifications = Lists.newArrayList();

        if (config.isNotifyEmail() && config.isNotifySms()) {
            notifications.add(createNotification(config, edu, TransportType.EMAIL, 0));
            notifications.add(createNotification(config, edu, TransportType.EMAIL, 7));
            notifications.add(createNotification(config, edu, TransportType.SMS, 0));
            notifications.add(createNotification(config, edu, TransportType.SMS, 7));
        } else if (config.isNotifySms()) {
            notifications.add(createNotification(config, edu, TransportType.SMS, 0));
            notifications.add(createNotification(config, edu, TransportType.SMS, 7));
        } else if (config.isNotifyEmail()){
            notifications.add(createNotification(config, edu, TransportType.EMAIL, 0));
            notifications.add(createNotification(config, edu, TransportType.EMAIL, 7));
        } else {
            notifications.add(createNotification(config, edu, null, 0));
            notifications.add(createNotification(config, edu, null, 7));
        }

        return notifications;
    }

    private static Notification2009 createNotification(CorrespondenceAgencyConfiguration config, EDUCore edu,
                                                       TransportType type, int delayInDays) {

        Notification2009 notification = new Notification2009();
        no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory notificationFactory = new no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory();
        notification.setFromAddress(notificationFactory.createNotification2009FromAddress("no-reply@altinn.no"));
        // The date and time the notification should be sent
        if (delayInDays == 0) {
            notification.setShipmentDateTime(toXmlGregorianCalendar(ZonedDateTime.now().plusMinutes(5)));
        } else {
            notification.setShipmentDateTime(toXmlGregorianCalendar(ZonedDateTime.now().plusDays(delayInDays)));
        }
        // Language code of the notification
        notification.setLanguageCode(notificationFactory.createNotification2009LanguageCode("1044"));
        // Notification type
        if (type == null) {
            notification.setNotificationType(notificationFactory.createNotification2009NotificationType("offentlig_etat"));
            notification.setTextTokens(notificationFactory.createNotification2009TextTokens(createTokens(config, null,
                    edu)));
            JAXBElement<ReceiverEndPointBEList> receiverEndpoints = createReceiverEndPoint(type);
            notification.setReceiverEndPoints(receiverEndpoints);
        } else {
            notification.setNotificationType(notificationFactory.createNotification2009NotificationType("TokenTextOnly"));
            notification.setTextTokens(notificationFactory.createNotification2009TextTokens(createTokens(config, type,
                    edu)));
            JAXBElement<ReceiverEndPointBEList> receiverEndpoints = createReceiverEndPoint(type);
            notification.setReceiverEndPoints(receiverEndpoints);
        }


        JAXBElement<ReceiverEndPointBEList> receiverEndpoints = createReceiverEndPoint(type);
        notification.setReceiverEndPoints(receiverEndpoints);

        return notification;
    }

    private static TextTokenSubstitutionBEList createTokens(CorrespondenceAgencyConfiguration config,
                                                                TransportType type,
                                                                EDUCore edu) {

        TextTokenSubstitutionBEList tokens = new TextTokenSubstitutionBEList();

        if (type == null) {
            tokens.getTextToken().add(createTextToken(0, edu.getSender().getName()));
            tokens.getTextToken().add(createTextToken(1, serviceEditionMapping.get(Integer.valueOf(getServiceEditionCode
                    (config).getValue()))));
            tokens.getTextToken().add(createTextToken(2, edu.getReceiver().getName()));
            return tokens;
        }

        switch (type) {
            case EMAIL:
                tokens.getTextToken().add(createTextToken(0, config.getEmailSubject()));
                tokens.getTextToken().add(createTextToken(1, config.getEmailBody()));
                break;
            case SMS:
                tokens.getTextToken().add(createTextToken(0, config.getSmsText()));
                tokens.getTextToken().add(createTextToken(1, ""));
                break;
            default:
                tokens.getTextToken().add(createTextToken(0, edu.getSender().getName()));
                tokens.getTextToken().add(createTextToken(1, serviceEditionMapping.get(Integer.valueOf(getServiceEditionCode
                        (config).getValue()))));
                tokens.getTextToken().add(createTextToken(2, edu.getReceiver().getName()));
        }

        return tokens;
    }

    private static ZonedDateTime getAllowSystemDeleteDateTime(EDUCore edu) {
        if (edu.getMessageType() == EDUCore.MessageType.EDU) {
            return ZonedDateTime.now().plusMinutes(5);
        } else {
            return ZonedDateTime.now().plusMinutes(5);
        }
    }

    public static GetCorrespondenceStatusDetailsV2 createReceiptRequest(Conversation conversation) {

        no.altinn.services.serviceengine.correspondence._2009._10.ObjectFactory of = new no.altinn.services
                .serviceengine.correspondence._2009._10.ObjectFactory();
        GetCorrespondenceStatusDetailsV2 statusRequest = of.createGetCorrespondenceStatusDetailsV2();

        CorrespondenceStatusFilterV2 filter = new CorrespondenceStatusFilterV2();
        no.altinn.schemas.services.serviceengine.correspondence._2014._10.ObjectFactory filterOF = new no.altinn
                .schemas.services.serviceengine.correspondence._2014._10.ObjectFactory();
        JAXBElement<String> sendersReference = filterOF.createCorrespondenceStatusFilterV2SendersReference
                (conversation.getConversationId());
        filter.setSendersReference(sendersReference);
        filter.setServiceCode("4255");
        filter.setServiceEditionCode(10);
        statusRequest.setFilterCriteria(filter);

        return statusRequest;
    }

    private static JAXBElement<String> getServiceCode(CorrespondenceAgencyConfiguration postConfig) {
        String serviceCodeProp= postConfig.getExternalServiceCode();
        String serviceCode= !Strings.isNullOrEmpty(serviceCodeProp) ? serviceCodeProp : "4255";
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createMyInsertCorrespondenceV2ServiceCode(serviceCode);
    }

    private static JAXBElement<String> getServiceEditionCode(CorrespondenceAgencyConfiguration postConfig) {
        String serviceEditionProp = postConfig.getExternalServiceEditionCode();
        String serviceEdition = !Strings.isNullOrEmpty(serviceEditionProp) ? serviceEditionProp : "10";
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createMyInsertCorrespondenceV2ServiceEdition(serviceEdition);
    }

    private static TextToken createTextToken(int num, String value) {
        no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory objectFactory = new no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory();
        TextToken textToken = new TextToken();
        textToken.setTokenNum(num);
        textToken.setTokenValue(objectFactory.createTextTokenTokenValue(value));

        return textToken;
    }

    private static JAXBElement<ReceiverEndPointBEList> createReceiverEndPoint(TransportType type) {
        no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory objectFactory = new no.altinn.schemas.services.serviceengine.notification._2009._10.ObjectFactory();
        ReceiverEndPoint receiverEndPoint = new ReceiverEndPoint();
        receiverEndPoint.setTransportType(objectFactory.createReceiverEndPointTransportType(type));
        ReceiverEndPointBEList receiverEndpoints = new ReceiverEndPointBEList();
        receiverEndpoints.getReceiverEndPoint().add(receiverEndPoint);
        return objectFactory.createNotification2009ReceiverEndPoints(receiverEndpoints);
    }

    private static XMLGregorianCalendar toXmlGregorianCalendar(ZonedDateTime date) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(date));
        } catch (DatatypeConfigurationException e) {
            throw new RuntimeException("Could not convert ZonedDateTime(value="+date+") to " + XMLGregorianCalendar.class, e);
        }
    }

}
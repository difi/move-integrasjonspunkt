package no.difi.meldingsutveksling.noarkexchange.altinn;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.LogstashMarker;
import net.logstash.logback.marker.Markers;
import no.difi.meldingsutveksling.*;
import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.domain.MessageInfo;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.kvittering.SBDReceiptFactory;
import no.difi.meldingsutveksling.kvittering.xsd.Kvittering;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.nextmove.NextMoveOutMessage;
import no.difi.meldingsutveksling.nextmove.NextMoveQueue;
import no.difi.meldingsutveksling.nextmove.StatusMessage;
import no.difi.meldingsutveksling.nextmove.message.MessagePersister;
import no.difi.meldingsutveksling.noarkexchange.receive.InternalQueue;
import no.difi.meldingsutveksling.receipt.*;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookupException;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import no.difi.meldingsutveksling.transport.Transport;
import no.difi.meldingsutveksling.transport.TransportFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.time.LocalDateTime;
import java.util.List;

import static java.lang.String.format;
import static no.difi.meldingsutveksling.ServiceIdentifier.DPO;
import static no.difi.meldingsutveksling.domain.sbdh.SBDUtil.*;
import static no.difi.meldingsutveksling.logging.MessageMarkerFactory.markerFrom;

@Slf4j
@Component
@ConditionalOnProperty(name = "difi.move.feature.enableDPO", havingValue = "true")
@RequiredArgsConstructor
public class DpoPolling {

    private final IntegrasjonspunktProperties properties;
    private final InternalQueue internalQueue;
    private final IntegrasjonspunktNokkel keyInfo;
    private final TransportFactory transportFactory;
    private final ServiceRegistryLookup serviceRegistryLookup;
    private final ConversationService conversationService;
    private final NextMoveQueue nextMoveQueue;
    private final MessagePersister messagePersister;
    private final AltinnWsClientFactory altinnWsClientFactory;
    private final ApplicationContextHolder applicationContextHolder;
    private final SBDReceiptFactory sbdReceiptFactory;
    private final MessageStatusFactory messageStatusFactory;

    private ServiceRecord serviceRecord;

    void poll() {
        if (!properties.getFeature().isEnableDPO()) {
            return;
        }
        log.debug("Checking for new messages");

        if (serviceRecord == null) {
            try {
                serviceRecord = serviceRegistryLookup.getServiceRecord(properties.getOrg().getNumber(), DPO);
            } catch (ServiceRegistryLookupException e) {
                throw new MeldingsUtvekslingRuntimeException(String.format("DPO ServiceRecord not found for %s", properties.getOrg().getNumber()), e);
            }
        }

        AltinnWsClient client = altinnWsClientFactory.getAltinnWsClient(serviceRecord);

        List<FileReference> fileReferences = client.availableFiles(properties.getOrg().getNumber());

        if (!fileReferences.isEmpty()) {
            log.debug("New message(s) detected");
        }

        for (FileReference reference : fileReferences) {
            try {
                final DownloadRequest request = new DownloadRequest(reference.getValue(), properties.getOrg().getNumber());
                log.debug(format("Downloading message with altinnId=%s", reference.getValue()));
                StandardBusinessDocument sbd = client.download(request, messagePersister);
                Audit.info(format("Downloaded message with id=%s", sbd.getConversationId()), sbd.createLogstashMarkers());

                if (isNextMove(sbd)) {
                    log.debug(format("NextMove message id=%s", sbd.getConversationId()));
                    if (isStatus(sbd)) {
                        StatusMessage status = (StatusMessage) sbd.getAny();
                        MessageStatus ms = messageStatusFactory.getMessageStatus(status.getStatus());
                        conversationService.registerStatus(sbd.getConversationId(), ms);
                    } else {
                        if (properties.getNoarkSystem().isEnable() && !properties.getNoarkSystem().getEndpointURL().isEmpty()) {
                            internalQueue.enqueueNoark(sbd);
                        } else {
                            nextMoveQueue.enqueue(sbd, DPO);
                        }
                        sendReceivedStatusToSender(sbd);
                    }

                } else {
                    if (isReceipt(sbd)) {
                        JAXBElement<Kvittering> jaxbKvit = (JAXBElement<Kvittering>) sbd.getAny();
                        Audit.info(format("Message id=%s is a receipt", sbd.getConversationId()),
                                sbd.createLogstashMarkers().and(getReceiptTypeMarker(jaxbKvit.getValue())));
                        MessageStatus status = statusFromKvittering(jaxbKvit.getValue());
                        conversationService.registerStatus(sbd.getConversationId(), status);
                    } else {
                        sendReceipt(sbd.getMessageInfo());
                        log.debug(sbd.createLogstashMarkers(), "Delivery receipt sent");
                        Conversation c = conversationService.registerConversation(sbd);
                        internalQueue.enqueueNoark(sbd);
                        conversationService.registerStatus(c, messageStatusFactory.getMessageStatus(ReceiptStatus.INNKOMMENDE_MOTTATT));
                    }
                }

                client.confirmDownload(request);
                log.debug(markerFrom(reference).and(sbd.createLogstashMarkers()), "Message confirmed downloaded");

            } catch (Exception e) {
                log.error(format("Error during Altinn message polling, message altinnId=%s", reference.getValue()), e);
            }
        }
    }

    private LogstashMarker getReceiptTypeMarker(Kvittering kvittering) {
        final String field = "receipt-type";
        if (kvittering.getLevering() != null) {
            return Markers.append(field, "levering");
        }
        if (kvittering.getAapning() != null) {
            return Markers.append(field, "åpning");
        }
        return Markers.append(field, "unkown");
    }

    private MessageStatus statusFromKvittering(Kvittering kvittering) {
        ReceiptStatus status = DpoReceiptMapper.from(kvittering);
        LocalDateTime tidspunkt = kvittering.getTidspunkt().toGregorianCalendar().toZonedDateTime().toLocalDateTime();
        return messageStatusFactory.getMessageStatus(status, tidspunkt);
    }

    private void sendReceivedStatusToSender(StandardBusinessDocument sbd) {
        StandardBusinessDocument statusSbd = sbdReceiptFactory.createArkivmeldingStatusFrom(sbd, DocumentType.STATUS, ReceiptStatus.MOTTATT);
        NextMoveOutMessage msg = NextMoveOutMessage.of(statusSbd, DPO);
        internalQueue.enqueueNextMove2(msg);
    }

    private void sendReceipt(MessageInfo messageInfo) {
        StandardBusinessDocument doc = sbdReceiptFactory.createLeveringsKvittering(messageInfo, keyInfo);
        Transport t = transportFactory.createTransport(doc);
        t.send(applicationContextHolder.getApplicationContext(), doc);
    }
}
package no.difi.meldingsutveksling.noarkexchange;

import net.logstash.logback.encoder.org.apache.commons.lang.StringUtils;
import net.logstash.logback.marker.LogstashMarker;
import no.difi.meldingsutveksling.config.IntegrasjonspunktConfiguration;
import no.difi.meldingsutveksling.core.EDUCore;
import no.difi.meldingsutveksling.core.EDUCoreFactory;
import no.difi.meldingsutveksling.domain.MeldingsUtvekslingRuntimeException;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.logging.MarkerFactory;
import no.difi.meldingsutveksling.noarkexchange.putmessage.MessageStrategy;
import no.difi.meldingsutveksling.noarkexchange.putmessage.MessageStrategyFactory;
import no.difi.meldingsutveksling.noarkexchange.putmessage.StrategyFactory;
import no.difi.meldingsutveksling.noarkexchange.receive.InternalQueue;
import no.difi.meldingsutveksling.noarkexchange.schema.*;
import no.difi.meldingsutveksling.serviceregistry.ServiceRegistryLookup;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import no.difi.meldingsutveksling.services.Adresseregister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.BindingType;

import static no.difi.meldingsutveksling.noarkexchange.PutMessageMarker.markerFrom;


/**
 * This is the implementation of the wenbservice that case managenent systems supporting
 * the BEST/EDU stadard communicates with. The responsibility of this component is to
 * create, sign and encrypt a SBD message for delivery to a PEPPOL access point
 * <p/>
 * The access point for the recipient is looked up through ELMA and SMK, the certificates are
 * retrived through a MOCKED adress register component not yet imolemented in any infrastructure.
 * <p/>
 * <p/>
 * User: glennbech
 * Date: 31.10.14
 * Time: 15:26
 */

@org.springframework.stereotype.Component("noarkExchangeService")
@WebService(portName = "NoarkExchangePort", serviceName = "noarkExchange", targetNamespace = "http://www.arkivverket.no/Noark/Exchange", endpointInterface = "no.difi.meldingsutveksling.noarkexchange.schema.SOAPport")
@BindingType("http://schemas.xmlsoap.org/wsdl/soap/http")
public class IntegrasjonspunktImpl implements SOAPport {
    private static final Logger log = LoggerFactory.getLogger(IntegrasjonspunktImpl.class);

    @Autowired
    private MessageSender messageSender;

    @Autowired
    private NoarkClient mshClient;

    @Autowired
    private InternalQueue internalQueue;

    @Autowired
    private IntegrasjonspunktConfiguration configuration;

    @Autowired
    private Adresseregister adresseRegister;

    @Autowired
    private StrategyFactory strategyFactory;

    @Autowired
    ServiceRegistryLookup serviceRegistryLookup;

    @Override
    public GetCanReceiveMessageResponseType getCanReceiveMessage(@WebParam(name = "GetCanReceiveMessageRequest", targetNamespace = "http://www.arkivverket.no/Noark/Exchange/types", partName = "getCanReceiveMessageRequest") GetCanReceiveMessageRequestType getCanReceiveMessageRequest) {


        String organisasjonsnummer = getCanReceiveMessageRequest.getReceiver().getOrgnr();

        GetCanReceiveMessageResponseType response = new GetCanReceiveMessageResponseType();
        boolean canReceive;

        canReceive = adresseRegister.hasAdresseregisterCertificate(organisasjonsnummer);

        final LogstashMarker marker = MarkerFactory.receiverMarker(organisasjonsnummer);
        if(canReceive) {
            Audit.info("CanReceive = true", marker);
        } else {
            if(hasMshEndpoint()) {
                canReceive = mshClient.canRecieveMessage(organisasjonsnummer);
                Audit.info(String.format( "MSH canReceive = %s", canReceive), marker);
            }
        }
        if(!canReceive) {
            Audit.error("CanReceive = false", marker);
        }
        response.setResult(canReceive);
        return response;
    }

    private boolean hasMshEndpoint(){
        return !StringUtils.isBlank(configuration.getKeyMshEndpoint());
    }

    @Override
    public PutMessageResponseType putMessage(PutMessageRequestType request) {
        MDC.put(IntegrasjonspunktConfiguration.KEY_ORGANISATION_NUMBER, configuration.getOrganisationNumber());
        PutMessageRequestWrapper message = new PutMessageRequestWrapper(request);
        if (!message.hasSenderPartyNumber()) {
            message.setSenderPartyNumber(configuration.getOrganisationNumber());
        }

        Audit.info("Received EDU message", markerFrom(message));

        if(PayloadUtil.isEmpty(message.getPayload())){
            Audit.error("Payload is missing", markerFrom(message));
            if(configuration.getReturnOkOnMissingPayload()){
                return PutMessageResponseFactory.createOkResponse();
            }
            else {
                return PutMessageResponseFactory.createErrorResponse( new MessageException(StatusMessage.MISSING_PAYLOAD));
            }
        }

        // Validate sender identifier
        if (!message.hasSenderPartyNumber()) {
            Audit.error("Senders orgnr missing", markerFrom(message));
            throw new MeldingsUtvekslingRuntimeException("Missing senders orgnumber. Please configure orgnumber= in the integrasjonspunkt-local.properties");
        }
        // Validate receiver identifier
        if (!message.hasRecieverPartyNumber()) {
            Audit.error("Receiver orgnr missing", markerFrom(message));
            throw new MeldingsUtvekslingRuntimeException("Missing receivers orgnumber.");
        }

        EDUCoreFactory eduCoreFactory = new EDUCoreFactory(serviceRegistryLookup);
        EDUCore coreMessage = eduCoreFactory.create(message.getRequest(), message.getSenderPartynumber());

        if (configuration.isQueueEnabled()) {
            internalQueue.enqueueExternal(coreMessage);
            Audit.info("Message enqueued", markerFrom(message));

            return PutMessageResponseFactory.createOkResponse();
        }
        else {
            Audit.info("Queue is disabled", markerFrom(message));

            if (adresseRegister.hasAdresseregisterCertificate(coreMessage.getReceiver().getOrgNr())) {
                final ServiceRecord serviceRecord = serviceRegistryLookup.getPrimaryServiceRecord(coreMessage
                        .getReceiver().getOrgNr());
                MessageStrategyFactory messageStrategyFactory = this.strategyFactory.getFactory(serviceRecord);
                MessageStrategy strategy = messageStrategyFactory.create(request.getPayload());
                return strategy.putMessage(coreMessage);
            } else {
                if(hasMshEndpoint()) {
                    Audit.info("Send message to MSH", markerFrom(message));
                    return mshClient.sendEduMelding(request);
                }
                Audit.error("Receiver not found", markerFrom(message));
                return PutMessageResponseFactory.createErrorResponse(new MessageException(StatusMessage.UNABLE_TO_FIND_RECEIVER));
            }
        }
    }

    public MessageSender getMessageSender() {
        return messageSender;
    }

    public void setMessageSender(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public void setMshClient(NoarkClient mshClient) {
        this.mshClient = mshClient;
    }

    public NoarkClient getMshClient() {
        return mshClient;
    }

    private static boolean validateResult(PutMessageResponseType response) {
        return "OK".equals(response.getResult().getType());
    }

    public void setAdresseRegister(Adresseregister adresseRegister) {
        this.adresseRegister = adresseRegister;
    }
}

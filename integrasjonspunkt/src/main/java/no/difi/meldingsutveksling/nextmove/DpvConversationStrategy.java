package no.difi.meldingsutveksling.nextmove;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.altinn.services.serviceengine.correspondence._2009._10.InsertCorrespondenceV2;
import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.core.BestEduConverter;
import no.difi.meldingsutveksling.noarkexchange.*;
import no.difi.meldingsutveksling.noarkexchange.schema.AppReceiptType;
import no.difi.meldingsutveksling.noarkexchange.schema.PutMessageRequestType;
import no.difi.meldingsutveksling.pipes.PromiseMaker;
import no.difi.meldingsutveksling.ptv.CorrespondenceAgencyClient;
import no.difi.meldingsutveksling.ptv.CorrespondenceAgencyMessageFactory;
import no.difi.meldingsutveksling.receipt.ConversationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static no.difi.meldingsutveksling.logging.NextMoveMessageMarkers.markerFrom;
import static no.difi.meldingsutveksling.ptv.WithLogstashMarker.withLogstashMarker;

@Component
@Slf4j
@ConditionalOnProperty(name = "difi.move.feature.enableDPV", havingValue = "true")
@RequiredArgsConstructor
public class DpvConversationStrategy implements ConversationStrategy {

    private final CorrespondenceAgencyMessageFactory correspondenceAgencyMessageFactory;
    private final CorrespondenceAgencyClient client;
    private final ConversationService conversationService;
    private final IntegrasjonspunktProperties props;
    private final NoarkClient localNoark;
    private final PutMessageRequestFactory putMessageRequestFactory;
    private final ConversationIdEntityRepo conversationIdEntityRepo;
    private final PromiseMaker promiseMaker;

    @Override
    @Transactional
    public void send(NextMoveOutMessage message) {

        promiseMaker.promise(reject -> {
            InsertCorrespondenceV2 correspondence = correspondenceAgencyMessageFactory.create(message, reject);

            Object response = withLogstashMarker(markerFrom(message))
                    .execute(() -> client.sendCorrespondence(correspondence));

            if (response == null) {
                throw new NextMoveRuntimeException("Failed to create Correspondence Agency Request");
            }

            String serviceCode = correspondence.getCorrespondence().getServiceCode().getValue();
            String serviceEditionCode = correspondence.getCorrespondence().getServiceEdition().getValue();
            conversationService.findConversation(message.getMessageId())
                    .ifPresent(conversation -> conversationService.save(conversation
                            .setServiceCode(serviceCode)
                            .setServiceEditionCode(serviceEditionCode)));
            return null;
        }).await();

        if (!isNullOrEmpty(props.getNoarkSystem().getType())) {
            sendAppReceipt(message);
        }
    }

    private void sendAppReceipt(NextMoveOutMessage message) {
        String conversationId = message.getConversationId();
        ConversationIdEntity convId = conversationIdEntityRepo.findByNewConversationId(message.getConversationId());
        if (convId != null) {
            log.warn("Found {} which maps to conversation {} with invalid UUID - overriding in AppReceipt.", message.getConversationId(), convId.getOldConversationId());
            conversationId = convId.getOldConversationId();
            conversationIdEntityRepo.delete(convId);
        }
        AppReceiptType appReceipt = AppReceiptFactory.from("OK", "None", "OK");
        PutMessageRequestType putMessage = putMessageRequestFactory.create(message.getSbd(),
                BestEduConverter.appReceiptAsString(appReceipt),
                conversationId);
        localNoark.sendEduMelding(putMessage);
    }
}

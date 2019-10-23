package no.difi.meldingsutveksling.nextmove.v2;

import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.difi.asic.AsicUtils;
import no.difi.meldingsutveksling.DocumentType;
import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.exceptions.FileNotFoundException;
import no.difi.meldingsutveksling.exceptions.MessageNotFoundException;
import no.difi.meldingsutveksling.exceptions.MessageNotLockedException;
import no.difi.meldingsutveksling.exceptions.NoContentException;
import no.difi.meldingsutveksling.kvittering.SBDReceiptFactory;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.nextmove.NextMoveInMessage;
import no.difi.meldingsutveksling.nextmove.NextMoveOutMessage;
import no.difi.meldingsutveksling.nextmove.message.CryptoMessagePersister;
import no.difi.meldingsutveksling.nextmove.message.FileEntryStream;
import no.difi.meldingsutveksling.noarkexchange.receive.InternalQueue;
import no.difi.meldingsutveksling.receipt.ConversationService;
import no.difi.meldingsutveksling.receipt.MessageStatusFactory;
import no.difi.meldingsutveksling.receipt.ReceiptStatus;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.persistence.PersistenceException;
import javax.validation.Valid;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import static java.lang.String.format;
import static no.difi.meldingsutveksling.NextMoveConsts.ASIC_FILE;
import static no.difi.meldingsutveksling.ServiceIdentifier.DPE;
import static no.difi.meldingsutveksling.ServiceIdentifier.DPO;
import static no.difi.meldingsutveksling.logging.NextMoveMessageMarkers.markerFrom;

@RestController
@Validated
@Api(tags = "Incoming messages")
@RequestMapping("/api/messages/in")
@Slf4j
@RequiredArgsConstructor
public class NextMoveMessageInController {

    private static final MediaType MIMETYPE_ASICE = MediaType.parseMediaType(AsicUtils.MIMETYPE_ASICE);
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_FILENAME = "attachment; filename=";

    private final IntegrasjonspunktProperties props;
    private final ConversationService conversationService;
    private final NextMoveMessageInRepository messageRepo;
    private final CryptoMessagePersister cryptoMessagePersister;
    private final InternalQueue internalQueue;
    private final SBDReceiptFactory receiptFactory;
    private final MessageStatusFactory messageStatusFactory;
    private final Clock clock;

    @GetMapping
    @ApiOperation(value = "Find incoming messages")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Success", response = StandardBusinessDocument[].class),
            @ApiResponse(code = 400, message = "Bad Request", response = String.class),
            @ApiResponse(code = 404, message = "Not found", response = String.class),
            @ApiResponse(code = 204, message = "No content", response = String.class)
    })
    @Transactional
    public Page<StandardBusinessDocument> findMessages(
            @Valid NextMoveInMessageQueryInput input,
            @PageableDefault Pageable pageable) {
        return messageRepo.find(input, pageable);
    }

    @GetMapping(value = "peek")
    @ApiOperation(value = "Peek and lock incoming queue", notes = "Gets the first message in the incoming queue, then locks the message")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Success", response = StandardBusinessDocument.class),
            @ApiResponse(code = 204, message = "No content", response = String.class),
            @ApiResponse(code = 400, message = "Bad Request", response = String.class)
    })
    @Transactional
    public StandardBusinessDocument peek(@Valid NextMoveInMessageQueryInput input) {
        NextMoveInMessage message = messageRepo.peek(input)
                .orElseThrow(NoContentException::new);

        messageRepo.save(message.setLockTimeout(OffsetDateTime.now(clock)
                .plusMinutes(props.getNextmove().getLockTimeoutMinutes())));

        log.info(markerFrom(message), "Message with id={} locked", message.getMessageId());
        return message.getSbd();
    }

    @GetMapping(value = "pop/{messageId}")
    @ApiOperation(value = "Pop incoming queue", notes = "Gets the ASiC for the first locked message in the queue, " +
            "unless messageId is specified.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Success", response = InputStreamResource.class),
            @ApiResponse(code = 204, message = "No content", response = String.class),
            @ApiResponse(code = 400, message = "Bad Request", response = String.class)
    })
    @Transactional
    public ResponseEntity<InputStreamResource> popMessage(
            @ApiParam(value = "MessageId", required = true)
            @PathVariable("messageId") String messageId) {

        NextMoveInMessage message = messageRepo.findByMessageId(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        if (message.getLockTimeout() == null) {
            throw new MessageNotLockedException(messageId);
        }

        try {
            FileEntryStream fileEntry = cryptoMessagePersister.readStream(messageId, ASIC_FILE, throwable ->
                    Audit.error(String.format("Can not read file \"%s\" for message [messageId=%s, sender=%s].",
                            ASIC_FILE, message.getMessageId(), message.getSenderIdentifier()), markerFrom(message), throwable)
            );
            return ResponseEntity.ok()
                    .header(HEADER_CONTENT_DISPOSITION, HEADER_FILENAME + ASIC_FILE)
                    .contentType(MIMETYPE_ASICE)
                    .body(new InputStreamResource(fileEntry.getInputStream()));
        } catch (PersistenceException e) {
            Audit.error(String.format("Can not read file \"%s\" for message [messageId=%s, sender=%s].",
                    ASIC_FILE, message.getMessageId(), message.getSenderIdentifier()), markerFrom(message), e);
            throw new FileNotFoundException(ASIC_FILE);
        }
    }

    @DeleteMapping(value = "/{messageId}")
    @ApiOperation(value = "Remove message", notes = "Delete message")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Success", response = StandardBusinessDocument.class),
            @ApiResponse(code = 400, message = "Bad Request", response = String.class),
            @ApiResponse(code = 404, message = "Not Found", response = String.class)
    })
    @Transactional
    public StandardBusinessDocument deleteMessage(
            @ApiParam(value = "MessageId", required = true)
            @PathVariable("messageId") String messageId) {
        NextMoveInMessage message = messageRepo.findByMessageId(messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));

        if (message.getLockTimeout() == null) {
            throw new MessageNotLockedException(messageId);
        }

        try {
            cryptoMessagePersister.delete(messageId);
        } catch (IOException e) {
            log.error("Error deleting files from message with id={}", messageId, e);
        }

        messageRepo.delete(message);

        conversationService.registerStatus(messageId,
                messageStatusFactory.getMessageStatus(ReceiptStatus.INNKOMMENDE_LEVERT));

        Audit.info(format("Message with id=%s popped from queue", messageId),
                markerFrom(message));

        if (message.getServiceIdentifier() == DPO) {
            StandardBusinessDocument statusSbd = receiptFactory.createArkivmeldingStatusFrom(message.getSbd(), DocumentType.STATUS, ReceiptStatus.LEVERT);
            NextMoveOutMessage msg = NextMoveOutMessage.of(statusSbd, DPO);
            internalQueue.enqueueNextMove(msg);
        }
        if (message.getServiceIdentifier() == DPE) {
            StandardBusinessDocument statusSbd = receiptFactory.createEinnsynStatusFrom(message.getSbd(), DocumentType.STATUS, ReceiptStatus.LEVERT);
            NextMoveOutMessage msg = NextMoveOutMessage.of(statusSbd, DPE);
            internalQueue.enqueueNextMove(msg);
        }

        return message.getSbd();
    }
}

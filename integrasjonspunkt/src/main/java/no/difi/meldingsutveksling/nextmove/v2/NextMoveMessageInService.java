package no.difi.meldingsutveksling.nextmove.v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.difi.meldingsutveksling.NextMoveConsts;
import no.difi.meldingsutveksling.api.ConversationService;
import no.difi.meldingsutveksling.api.CryptoMessagePersister;
import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.exceptions.AsicPersistenceException;
import no.difi.meldingsutveksling.exceptions.MessageNotFoundException;
import no.difi.meldingsutveksling.exceptions.MessageNotLockedException;
import no.difi.meldingsutveksling.exceptions.NoContentException;
import no.difi.meldingsutveksling.logging.Audit;
import no.difi.meldingsutveksling.nextmove.NextMoveInMessage;
import no.difi.meldingsutveksling.nextmove.NextMoveRuntimeException;
import no.difi.meldingsutveksling.nextmove.ResponseStatusSender;
import no.difi.meldingsutveksling.nextmove.message.BugFix610;
import no.difi.meldingsutveksling.nextmove.message.FileEntryStream;
import no.difi.meldingsutveksling.receipt.ReceiptStatus;
import org.slf4j.MDC;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.PersistenceException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;

import static java.lang.String.format;
import static no.difi.meldingsutveksling.NextMoveConsts.ASIC_FILE;
import static no.difi.meldingsutveksling.logging.NextMoveMessageMarkers.markerFrom;

@Slf4j
@Component
@RequiredArgsConstructor
public class NextMoveMessageInService {

    private final IntegrasjonspunktProperties props;
    private final ConversationService conversationService;
    private final NextMoveMessageInRepository messageRepo;
    private final CryptoMessagePersister cryptoMessagePersister;
    private final ResponseStatusSender responseStatusSender;
    private final Clock clock;

    @Transactional
    public Page<StandardBusinessDocument> findMessages(
            NextMoveInMessageQueryInput input, Pageable pageable) {
        return messageRepo.find(input, pageable);
    }

    @Transactional
    public StandardBusinessDocument peek(NextMoveInMessageQueryInput input) {
        NextMoveInMessage message = messageRepo.peek(input)
                .orElseThrow(NoContentException::new);
        MDC.put(NextMoveConsts.CORRELATION_ID, message.getMessageId());

        messageRepo.save(message.setLockTimeout(OffsetDateTime.now(clock)
                .plusMinutes(props.getNextmove().getLockTimeoutMinutes())));

        Audit.info(String.format("Message [id=%s] locked until %s", message.getMessageId(), message.getLockTimeout()), markerFrom(message));
        return message.getSbd();
    }

    @Transactional
    public InputStreamResource popMessage(String messageId) throws AsicPersistenceException {
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
            Audit.info(String.format("Pop - returning ASiC stream for message with id=%s", message.getMessageId()), markerFrom(message));
            return new InputStreamResource(getInputStream(fileEntry, messageId));

        } catch (PersistenceException e) {
            String errorMsg = format("Can not read file \"%s\" for message [messageId=%s, sender=%s], removing from queue.",
                    ASIC_FILE, message.getMessageId(), message.getSenderIdentifier());
            Audit.error(errorMsg, markerFrom(message), e);
            messageRepo.delete(message);
            conversationService.registerStatus(messageId, ReceiptStatus.FEIL, errorMsg);
            // throw checked AsicPersistanceException so that deletion transaction is not rolled back
            throw new AsicPersistenceException();
        }
    }

    private InputStream getInputStream(FileEntryStream fileEntry, String messageId) {
        if (props.getNextmove().getApplyZipHeaderPatch()) {
            try {
                return BugFix610.applyPatch(fileEntry.getInputStream(), messageId);
            } catch (IOException e) {
                throw new NextMoveRuntimeException("Could not apply patch 610 to message", e);
            }
        }

        return fileEntry.getInputStream();
    }

    @Transactional
    public StandardBusinessDocument deleteMessage(String messageId) {
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
        conversationService.registerStatus(messageId, ReceiptStatus.INNKOMMENDE_LEVERT);
        Audit.info(format("Message [id=%s, serviceIdentifier=%s] deleted from queue", messageId, message.getServiceIdentifier()),
                markerFrom(message));

        responseStatusSender.queue(message.getSbd(), message.getServiceIdentifier(), ReceiptStatus.LEVERT);

        return message.getSbd();
    }
}

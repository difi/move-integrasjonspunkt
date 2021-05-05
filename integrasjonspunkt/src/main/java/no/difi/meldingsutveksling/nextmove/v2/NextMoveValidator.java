package no.difi.meldingsutveksling.nextmove.v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import no.arkivverket.standarder.noark5.arkivmelding.Arkivmelding;
import no.difi.meldingsutveksling.MessageType;
import no.difi.meldingsutveksling.NextMoveConsts;
import no.difi.meldingsutveksling.ServiceIdentifier;
import no.difi.meldingsutveksling.api.ConversationService;
import no.difi.meldingsutveksling.api.OptionalCryptoMessagePersister;
import no.difi.meldingsutveksling.arkivmelding.ArkivmeldingUtil;
import no.difi.meldingsutveksling.domain.sbdh.SBDUtil;
import no.difi.meldingsutveksling.domain.sbdh.ScopeType;
import no.difi.meldingsutveksling.domain.sbdh.StandardBusinessDocument;
import no.difi.meldingsutveksling.exceptions.*;
import no.difi.meldingsutveksling.nextmove.*;
import no.difi.meldingsutveksling.serviceregistry.externalmodel.ServiceRecord;
import no.difi.meldingsutveksling.validation.Asserter;
import no.difi.meldingsutveksling.validation.IntegrasjonspunktCertificateValidator;
import no.difi.meldingsutveksling.validation.UUIDValidator;
import no.difi.meldingsutveksling.validation.VirksertCertificateException;
import no.difi.meldingsutveksling.validation.group.ValidationGroupFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateExpiredException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static no.difi.meldingsutveksling.MessageType.ARKIVMELDING;
import static no.difi.meldingsutveksling.MessageType.DIGITAL;
import static no.difi.meldingsutveksling.ServiceIdentifier.*;


@Component
@Slf4j
@RequiredArgsConstructor
public class NextMoveValidator {

    private final ServiceRecordProvider serviceRecordProvider;
    private final NextMoveMessageOutRepository messageRepo;
    private final ConversationStrategyFactory conversationStrategyFactory;
    private final Asserter asserter;
    private final OptionalCryptoMessagePersister optionalCryptoMessagePersister;
    private final TimeToLiveHelper timeToLiveHelper;
    private final SBDUtil sbdUtil;
    private final ConversationService conversationService;
    private final ArkivmeldingUtil arkivmeldingUtil;
    private final NextMoveFileSizeValidator fileSizeValidator;
    private final ObjectProvider<IntegrasjonspunktCertificateValidator> certificateValidator;

    void validate(StandardBusinessDocument sbd) {
        validateCertificate();

        // Need to validate scopes manually due to ReceiverRef can be non-UUID
        UUIDValidator uuidValidator = new UUIDValidator();
        sbd.getOptionalConversationId().ifPresent(cid -> {
            if (!uuidValidator.isValid(cid, null)) {
                throw new NotUUIDException(ScopeType.CONVERSATION_ID.getFullname(), cid);
            }
        });
        sbd.findScope(ScopeType.SENDER_REF).ifPresent(sref -> {
            if (!uuidValidator.isValid(sref.getInstanceIdentifier(), null)) {
                throw new NotUUIDException(ScopeType.SENDER_REF.getFullname(), sref.getInstanceIdentifier());
            }
        });

        sbd.getOptionalMessageId().ifPresent(messageId -> {
                    messageRepo.findByMessageId(messageId)
                            .map(p -> {
                                throw new MessageAlreadyExistsException(messageId);
                            });
                    if (!sbdUtil.isStatus(sbd)) {
                        conversationService.findConversation(messageId)
                                .map(c -> {
                                    throw new MessageAlreadyExistsException(messageId);
                                });
                    }
                }
        );

        ServiceRecord serviceRecord = serviceRecordProvider.getServiceRecord(sbd);
        ServiceIdentifier serviceIdentifier = serviceRecord.getServiceIdentifier();

        if (!conversationStrategyFactory.isEnabled(serviceIdentifier)) {
            throw new ServiceNotEnabledException(serviceIdentifier);
        }

        if (!BusinessMessageUtil.getMessageTypes().contains(sbd.getMessageType())) {
            throw new UnknownMessageTypeException(sbd.getMessageType());
        }

        if (!sbd.getDocumentType().endsWith("::"+sbd.getMessageType()) && serviceRecord.getServiceIdentifier() != DPFIO) {
            throw new MessageTypeDoesNotFitDocumentTypeException(sbd.getMessageType(), sbd.getDocumentType());
        }

        // Run validation for serviceIdentifiers
        Class<?> siGroup = ValidationGroupFactory.toServiceIdentifier(serviceIdentifier);
        asserter.isValid(sbd.getAny(), siGroup != null ? new Class<?>[] { siGroup } : new Class<?>[0]);

        // Run validation for internal message types
        // TODO: extendable validation groups for external message types
        MessageType.valueOfType(sbd.getMessageType()).ifPresent(mt -> {
            Class<?> mtGroup = ValidationGroupFactory.toMessageType(mt);
            asserter.isValid(sbd.getAny(), mtGroup != null ? new Class<?>[] { mtGroup } : new Class<?>[0]);
        });
    }

    @Transactional(noRollbackFor = TimeToLiveException.class)
    public void validate(NextMoveOutMessage message) {
        validateCertificate();

        // Must always be at least one attachment
        StandardBusinessDocument sbd = message.getSbd();
        if (sbdUtil.isFileRequired(sbd) && (message.getFiles() == null || message.getFiles().isEmpty())) {
            throw new MissingFileException();
        }

        sbd.getExpectedResponseDateTime().ifPresent(expectedResponseDateTime -> {
            if (sbdUtil.isExpired(sbd)) {
                timeToLiveHelper.registerErrorStatusAndMessage(sbd, message.getServiceIdentifier(), message.getDirection());
                throw new TimeToLiveException(expectedResponseDateTime);
            }
        });

        if (sbdUtil.isType(message.getSbd(), ARKIVMELDING)) {
            Set<String> messageFilenames = message.getFiles().stream()
                    .map(BusinessMessageFile::getFilename)
                    .collect(Collectors.toSet());
            // Verify each file referenced in arkivmelding is uploaded
            List<String> arkivmeldingFiles = arkivmeldingUtil.getFilenames(getArkivmelding(message));

            List<String> missingFiles = arkivmeldingFiles.stream()
                    .filter(p -> !messageFilenames.contains(p))
                    .collect(Collectors.toList());

            if (!missingFiles.isEmpty()) {
                throw new MissingArkivmeldingFileException(String.join(",", missingFiles));
            }
        }

        // Validate that files given in metadata mapping exist
        if (sbdUtil.isType(message.getSbd(), DIGITAL)) {
            Set<String> messageFilenames = message.getFiles().stream()
                    .map(BusinessMessageFile::getFilename)
                    .collect(Collectors.toSet());
            DpiDigitalMessage bmsg = (DpiDigitalMessage) message.getBusinessMessage();
            Set<String> filerefs = Stream.of(bmsg.getMetadataFiler().keySet(), bmsg.getMetadataFiler().values())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            if (!messageFilenames.containsAll(filerefs)) {
                String missing = filerefs.stream()
                        .filter(f -> !messageFilenames.contains(f))
                        .collect(Collectors.joining(", "));
                log.error("The following files were defined in metadata, but are missing as attachments: {}", missing);
                throw new FileNotFoundException(missing);
            }
        }

        if (message.getServiceIdentifier() == DPI && message.getFiles().stream()
                .noneMatch(BusinessMessageFile::getPrimaryDocument)) {
            throw new MissingPrimaryDocumentException();
        }
    }

    private void validateCertificate() {
        certificateValidator.ifAvailable(v -> {
            try {
                v.validateCertificate();
            } catch (CertificateExpiredException | VirksertCertificateException e) {
                log.error("Certificate validation failed", e);
                throw new InvalidCertificateException(e.getMessage());
            }
        });
    }

    private Arkivmelding getArkivmelding(NextMoveOutMessage message) {
        // Arkivmelding must exist for DPO
        BusinessMessageFile arkivmeldingFile = message.getFiles().stream()
                .filter(f -> NextMoveConsts.ARKIVMELDING_FILE.equals(f.getFilename()))
                .findAny()
                .orElseThrow(MissingArkivmeldingException::new);


        try (InputStream is = new ByteArrayInputStream(optionalCryptoMessagePersister.read(message.getMessageId(), arkivmeldingFile.getIdentifier()))) {
            return arkivmeldingUtil.unmarshalArkivmelding(is);
        } catch (JAXBException | IOException e) {
            throw new NextMoveRuntimeException("Failed to get Arkivmelding", e);
        }
    }

    void validateFile(NextMoveOutMessage message, MultipartFile file) {
        Set<BusinessMessageFile> files = message.getOrCreateFiles();
        files.stream()
                .map(BusinessMessageFile::getFilename)
                .filter(fn -> fn.equals(file.getOriginalFilename()))
                .findAny()
                .ifPresent(fn -> {
                    throw new DuplicateFilenameException(file.getOriginalFilename());
                });

        // Uncomplete message pre 2.1.1 might have size null.
        // Set to '-1' as workaround as validator accumulates total file size for message.
        message.getFiles().forEach(f -> {
            if (f.getSize() == null) f.setSize(-1L);
        });
        fileSizeValidator.validate(message, file);

        if (message.isPrimaryDocument(file.getOriginalFilename()) && files.stream().anyMatch(BusinessMessageFile::getPrimaryDocument)) {
            throw new MultiplePrimaryDocumentsNotAllowedException();
        }

        if (message.getServiceIdentifier() == DPV && !StringUtils.hasText(file.getName())) {
            throw new MissingFileTitleException(DPV.toString());
        }

        if (message.getServiceIdentifier() == DPI && !StringUtils.hasText(file.getName())) {
            if (!message.isPrimaryDocument(file.getOriginalFilename())) {
                if (message.getBusinessMessage() instanceof DpiDigitalMessage) {
                    DpiDigitalMessage bmsg = (DpiDigitalMessage) message.getBusinessMessage();
                    if (!bmsg.getMetadataFiler().containsValue(file.getOriginalFilename())) {
                        throw new MissingFileTitleException(DPI.toString());
                    }
                } else {
                    throw new MissingFileTitleException(DPI.toString());
                }
            }
        }

    }
}

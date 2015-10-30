package no.difi.meldingsutveksling.transport;

import no.difi.meldingsutveksling.domain.sbdh.Document;
import org.apache.commons.configuration.Configuration;

/**
 * Defines a transport. The responsibility of a transport is to receive an SBD ducument and transfer it over some
 * transportation mechanism; oxalis, Altinn, Dropbox or whatever.
 * <p/>
 * See individual modules for implementations of transports.
 *
 * @author Glenn bech
 * @see TransportFactory
 */
public interface Transport {

    /**
     * @param document An SBD document with a payload consisting of an CMS encrypted ASIC package
     */
    public void send(Configuration configuration, Document document);

}

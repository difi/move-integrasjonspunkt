package no.difi.meldingsutveksling.exceptions;

import org.springframework.http.HttpStatus;

public class MissingMessageTypeException extends HttpStatusCodeException {

    public MissingMessageTypeException() {
        super(HttpStatus.BAD_REQUEST, MissingMessageTypeException.class.getName());
    }
}

package no.difi.meldingsutveksling.nextmove.message;

import no.difi.meldingsutveksling.config.IntegrasjonspunktProperties;
import no.difi.meldingsutveksling.nextmove.DpoConversationResource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static java.nio.charset.StandardCharsets.UTF_8;

public class FileMessagePersisterTest {

    private IntegrasjonspunktProperties props;
    private DpoConversationResource cr;
    private FileMessagePersister messagePersister;

    @Before
    public void setup() {
        props = new IntegrasjonspunktProperties();
        IntegrasjonspunktProperties.NextMove nextMoveProps = new IntegrasjonspunktProperties.NextMove();
        nextMoveProps.setFiledir("target/filepersister_testdir");
        props.setNextmove(nextMoveProps);

        messagePersister = new FileMessagePersister(props);

        cr = DpoConversationResource.of("42", "2", "1");
    }

    @Test
    public void testFileMessagePersister() throws Exception {
        String filename = "foo";
        byte[] content = "bar".getBytes(UTF_8);

        messagePersister.write(cr.getConversationId(), filename, content);

        byte[] read = messagePersister.read(cr.getConversationId(), filename);
        Assert.assertArrayEquals(content, read);

        messagePersister.delete(cr.getConversationId());
        File crDir = new File(props.getNextmove().getFiledir() + "/" + cr.getConversationId());
        Assert.assertFalse(crDir.exists());
    }


}
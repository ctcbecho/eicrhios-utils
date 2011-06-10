package idv.kaomk.eicrhios.utils.encrypt;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;

@Command(scope = "jasypt", name = "encryption", description = "Encrypt/Decrypt a given text.")
public class EncryptionAction extends OsgiCommandSupport {
	private Encryptor mEncryptor;

	public void setEncryptor(Encryptor encryptor) {
		mEncryptor = encryptor;
	}

	@Argument(name = "text", description = "Text to be encrypted/decrypted.", required = true, multiValued = false)
	private String mText;

	@Option(name = "-d", aliases = { "--decode" }, description = "Decode the given text.", required = false, multiValued = false)
	private boolean mIsDecode;

	@Override
	protected Object doExecute() throws Exception {
		return mIsDecode ? mEncryptor.decrypt(mText) : mEncryptor
				.encrypt(mText);
	}

}

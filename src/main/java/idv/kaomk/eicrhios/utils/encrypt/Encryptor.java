package idv.kaomk.eicrhios.utils.encrypt;

import org.jasypt.util.text.StrongTextEncryptor;

public class Encryptor {
	private String mPassword;

	public void setPassword(String password) {
		mPassword = password;
	}
	
	private String endecrypt(boolean isEncrypt, String text){
		StrongTextEncryptor textEncryptor = new StrongTextEncryptor();
		textEncryptor.setPassword(mPassword);
		return isEncrypt? textEncryptor.encrypt(text):textEncryptor.decrypt(text);
	}
	public String encrypt(String text){
		return endecrypt(true, text);
	}
	
	public String decrypt(String text){
		return endecrypt(false, text);
	}
}

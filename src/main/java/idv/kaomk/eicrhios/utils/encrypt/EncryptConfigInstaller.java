package idv.kaomk.eicrhios.utils.encrypt;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;

import org.apache.felix.fileinstall.ArtifactInstaller;
import org.apache.felix.utils.collections.DictionaryAsMap;
import org.apache.felix.utils.properties.InterpolationHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.felix.utils.properties.Properties;

public class EncryptConfigInstaller implements ArtifactInstaller,
		ConfigurationListener {
	private Logger logger = LoggerFactory
			.getLogger(EncryptConfigInstaller.class);
	private static final String DISABLE_CONFIG_SAVE = "felix.fileinstall.filename";
	private static final String FILENAME = "felix.fileinstall.filename";
	private static final String ENCRYPT_FIELDS = "eicrhios.utils.encryptfields";

	private ConfigurationAdmin mConfigAdmin;
	private final BundleContext mContext;
	private Encryptor mEncryptor;

	public EncryptConfigInstaller(BundleContext context,
			ConfigurationAdmin configAdmin) {
		this.mContext = context;
		this.mConfigAdmin = configAdmin;
	}

	public void setEncryptor(Encryptor encryptor) {
		mEncryptor = encryptor;
	}

	@Override
	public boolean canHandle(File artifact) {
		return artifact.getName().endsWith(".ecfg");
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void configurationEvent(ConfigurationEvent configurationEvent) {
		if (configurationEvent.getType() == ConfigurationEvent.CM_UPDATED) {
			// Check if writing back configurations has been disabled.
			Object obj = mContext.getProperty(DISABLE_CONFIG_SAVE);
			if (obj instanceof String) {
				obj = new Boolean((String) obj);
			}
			if (Boolean.FALSE.equals(obj)) {
				return;
			}

			try {
				Configuration config = getConfigurationAdmin()
						.getConfiguration(configurationEvent.getPid(),
								configurationEvent.getFactoryPid());
				Dictionary dict = config.getProperties();
				String fileName = (String) dict.get(FILENAME);
				File file = fileName != null ? fromConfigKey(fileName) : null;
				if (file != null && file.isFile()) {
					Properties props = new Properties(file);
					for (Enumeration e = dict.keys(); e.hasMoreElements();) {
						String key = e.nextElement().toString();
						if (!Constants.SERVICE_PID.equals(key)
								&& !ConfigurationAdmin.SERVICE_FACTORYPID
										.equals(key) && !FILENAME.equals(key)) {
							String val = dict.get(key).toString();
							props.put(key, val);
						}
					}

					encryptConfig(props);
					props.save();

				}
			} catch (Exception e) {
				logger.error("Unable to save configuration", e);
			}
		}

	}

	@Override
	public void install(File artifact) throws Exception {
		setConfig(artifact);

	}

	@Override
	public void uninstall(File artifact) throws Exception {
		deleteConfig(artifact);

	}

	@Override
	public void update(File artifact) throws Exception {
		setConfig(artifact);

	}

	/**
	 * Set the configuration based on the config file.
	 * 
	 * @param f
	 *            Configuration file
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	boolean setConfig(final File f) throws Exception {
		final Hashtable ht = new Hashtable();
		final InputStream in = new BufferedInputStream(new FileInputStream(f));
		try {
			final Properties p = new Properties();
			in.mark(1);
			boolean isXml = in.read() == '<';
			in.reset();
			if (isXml) {
				throw new UnsupportedEncodingException(
						"No support for xml type properties file.");
			} else {
				p.load(in);
			}
			decryptConfig(p);
			InterpolationHelper.performSubstitution((Map) p, mContext);
			ht.putAll(p);
		} finally {
			in.close();
		}

		String pid[] = parsePid(f.getName());
		Configuration config = getConfiguration(toConfigKey(f), pid[0], pid[1]);

		Dictionary props = config.getProperties();
		Hashtable old = props != null ? new Hashtable(
				new DictionaryAsMap(props)) : null;
		if (old != null) {
			old.remove(FILENAME);
			old.remove(Constants.SERVICE_PID);
			old.remove(ConfigurationAdmin.SERVICE_FACTORYPID);
		}

		if (!ht.equals(old)) {
			ht.put(FILENAME, toConfigKey(f));
			if (config.getBundleLocation() != null) {
				config.setBundleLocation(null);
			}
			config.update(ht);
			return true;
		} else {
			return false;
		}
	}

	String[] parsePid(String path) {
		String pid = path.substring(0, path.lastIndexOf("."));
		int n = pid.indexOf('-');
		if (n > 0) {
			String factoryPid = pid.substring(n + 1);
			pid = pid.substring(0, n);
			return new String[] { pid, factoryPid };
		} else {
			return new String[] { pid, null };
		}
	}

	String toConfigKey(File f) {
		return f.getAbsoluteFile().toURI().toString();
	}

	Configuration getConfiguration(String fileName, String pid,
			String factoryPid) throws Exception {
		Configuration oldConfiguration = findExistingConfiguration(fileName);
		if (oldConfiguration != null) {
			return oldConfiguration;
		} else {
			Configuration newConfiguration;
			if (factoryPid != null) {
				newConfiguration = getConfigurationAdmin()
						.createFactoryConfiguration(pid, null);
			} else {
				newConfiguration = getConfigurationAdmin().getConfiguration(
						pid, null);
			}
			return newConfiguration;
		}
	}

	Configuration findExistingConfiguration(String fileName) throws Exception {
		String filter = "(" + FILENAME + "=" + fileName + ")";
		Configuration[] configurations = getConfigurationAdmin()
				.listConfigurations(filter);
		if (configurations != null && configurations.length > 0) {
			return configurations[0];
		} else {
			return null;
		}
	}

	ConfigurationAdmin getConfigurationAdmin() {
		return mConfigAdmin;
	}

	File fromConfigKey(String key) {
		return new File(URI.create(key));
	}

	/**
	 * Remove the configuration.
	 * 
	 * @param f
	 *            File where the configuration in whas defined.
	 * @return
	 * @throws Exception
	 */
	boolean deleteConfig(File f) throws Exception {
		String pid[] = parsePid(f.getName());
		Configuration config = getConfiguration(toConfigKey(f), pid[0], pid[1]);
		config.delete();
		return true;
	}

	private void decryptConfig(Properties prop) {
		if (!prop.containsKey(ENCRYPT_FIELDS)) {
			return;
		}
		String[] decryptFields = prop.get(ENCRYPT_FIELDS).split("[\\s,]");
		for (String fieldKey : decryptFields) {
			if (prop.containsKey(fieldKey)) {
				try {
					prop.put(fieldKey, mEncryptor.decrypt(prop.get(fieldKey)));
				} catch (Exception e) {
					logger.warn(String.format(
							"decrypt failed for filed: %s , value: %s",
							fieldKey, prop.get(fieldKey)));
					prop.remove(fieldKey);
				}
			}
		}
	}

	private void encryptConfig(Properties prop) {
		if (!prop.containsKey(ENCRYPT_FIELDS)) {
			return;
		}

		String[] encryptFields = prop.get(ENCRYPT_FIELDS).split("[\\s,]");
		for (String fieldKey : encryptFields) {
			if (prop.containsKey(fieldKey)) {
				prop.put(fieldKey, mEncryptor.encrypt(prop.get(fieldKey)));
			}
		}
	}
}

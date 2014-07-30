package jef.tools.chinese;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.lang.ref.SoftReference;

import jef.tools.Assert;
import jef.tools.IOUtils;

public class ChineseCharProvider {
	static ChineseCharProvider instance;

	public enum Type {
		CHINESE_LAST_NAME
	}

	public static ChineseCharProvider getInstance() {
		if (instance == null)
			instance = new ChineseCharProvider();

		return instance;
	}

	public char[] get(Type type) {
		Assert.notNull(type);
		checkAndInit();
		switch (type) {
		case CHINESE_LAST_NAME:
			return LAST_NAMES.get();
		default:
			break;
		}
		throw new UnsupportedOperationException();
	}

	private SoftReference<char[]> LAST_NAMES;

	private void checkAndInit() {
		if (LAST_NAMES == null || LAST_NAMES.get() == null) {
			LAST_NAMES = new SoftReference<char[]>(load("lastname.properties", "UTF-8", 200));
		}
	}

	private static char[] load(String file, String charset, int i) {
		try {
			BufferedReader in = IOUtils.getReader(ChineseCharProvider.class, file, charset);
			CharArrayWriter sb = new CharArrayWriter(i);
			String s;
			while ((s = in.readLine()) != null) {
				sb.write(s);
			}
			return sb.toCharArray();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

}

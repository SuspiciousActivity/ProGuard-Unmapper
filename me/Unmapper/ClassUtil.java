package me.Unmapper;

import java.util.HashMap;

public class ClassUtil {

	public static String getSimpleDesc(String type) {
		String desc = "";
		for (int i = 0; i < countArraySize(type); i++) {
			desc += "[";
		}
		type = type.replace("[]", "");
		if (VARS.containsKey(type)) {
			desc += VARS.get(type);
		} else {
			desc += "L" + type + ";";
		}
		return desc;
	}

	private static int countArraySize(String type) {
		int size = 0;
		for (byte b : type.getBytes()) {
			if (b == ']') {
				size++;
			}
		}
		return size;
	}

	public static final HashMap<String, String> VARS = new HashMap<>();

	static {
		VARS.put("byte", "B");
		VARS.put("char", "C");
		VARS.put("double", "D");
		VARS.put("float", "F");
		VARS.put("int", "I");
		VARS.put("long", "J");
		VARS.put("short", "S");
		VARS.put("boolean", "Z");
		VARS.put("void", "V");
	}

}

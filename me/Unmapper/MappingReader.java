package me.Unmapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class MappingReader {

	private final File mappingFile;

	private final HashMap<String, String> internalReversedClassMappings = new HashMap<>();
	private final HashMap<String, String> classMappings = new HashMap<>();
	private final HashMap<MemberData, String> memberMappings = new HashMap<>();

	public MappingReader(File mappingFile) {
		this.mappingFile = mappingFile;
	}

	public void load() throws IOException {
		try (BufferedReader read = new BufferedReader(new FileReader(mappingFile))) {
			String className = null;
			String s;

			while ((s = read.readLine()) != null) {
				s = s.trim();

				if (s.startsWith("#"))
					continue;

				if (s.endsWith(":")) {
					className = parseClassMapping(s);
				} else if (className != null) {
					parseClassMemberMapping(className, s);
				}
			}
		}
	}

	private String parseClassMapping(String s) {
		int splitIdx = s.indexOf("->");
		if (splitIdx < 0) {
			return null;
		}

		int colonIndex = s.indexOf(':', splitIdx + 2);
		if (colonIndex < 0) {
			return null;
		}

		String originalName = s.substring(0, splitIdx).trim().replace('.', '/');
		String obfuscatedName = s.substring(splitIdx + 2, colonIndex).trim().replace('.', '/');

		classMappings.put(obfuscatedName, originalName);
		internalReversedClassMappings.put(originalName, obfuscatedName);

		return originalName;
	}

	private void parseClassMemberMapping(String className, String s) {
		int colonIdx1 = s.indexOf(':');
		int colonIdx2 = colonIdx1 < 0 ? -1 : s.indexOf(':', colonIdx1 + 1);
		int spaceIdx = s.indexOf(' ', colonIdx2 + 2);
		int argIdx1 = s.indexOf('(', spaceIdx + 1);
		int argIdx2 = argIdx1 < 0 ? -1 : s.indexOf(')', argIdx1 + 1);
		int colonIdx3 = argIdx2 < 0 ? -1 : s.indexOf(':', argIdx2 + 1);
		int colonIdx4 = colonIdx3 < 0 ? -1 : s.indexOf(':', colonIdx3 + 1);
		int arrowIdx = s.indexOf("->",
				(colonIdx4 >= 0 ? colonIdx4 : colonIdx3 >= 0 ? colonIdx3 : argIdx2 >= 0 ? argIdx2 : spaceIdx) + 1);

		if (spaceIdx < 0 || arrowIdx < 0) {
			return;
		}

		String type = s.substring(colonIdx2 + 1, spaceIdx).trim();
		String originalName = s.substring(spaceIdx + 1, argIdx1 >= 0 ? argIdx1 : arrowIdx).trim();
		String obfuscatedName = s.substring(arrowIdx + 2).trim();

		String newClassName = className;
		int dotIndex = originalName.lastIndexOf('.');
		if (dotIndex >= 0) {
			className = originalName.substring(0, dotIndex);
			originalName = originalName.substring(dotIndex + 1);
			// How does this work?
			System.out.println("Don't know how to handle mapping " + className + " to " + newClassName);
		}

		if (type.length() > 0 && originalName.length() > 0 && obfuscatedName.length() > 0) {
			if (argIdx2 < 0) {
				memberMappings.put(
						new MemberData(getOldClassName(className), convertTypeToByteCode(type), obfuscatedName),
						originalName);
			} else {
				String args = s.substring(argIdx1 + 1, argIdx2).trim();

				memberMappings.put(new MemberData(getOldClassName(className),
						"(" + convertMultipleTypesToByteCode(args) + ")" + convertTypeToByteCode(type), obfuscatedName),
						originalName);
			}
		}
	}

	public HashMap<String, String> getClassMappings() {
		return classMappings;
	}

	public HashMap<MemberData, String> getMemberMappings() {
		return memberMappings;
	}

	private String getOldClassName(String name) {
		int arrayCount = 0;
		int arrayIdx = name.lastIndexOf('[');
		while (arrayIdx >= 0) {
			arrayCount++;
			name = name.substring(0, arrayIdx);
			arrayIdx = name.lastIndexOf('[');
		}
		name = name.replace('.', '/');
		String old = internalReversedClassMappings.get(name);
		if (old == null)
			old = name;
		for (int i = 0; i < arrayCount; i++)
			old += "[]";
		return old;
	}

	private String convertTypeToByteCode(String type) {
		if (type.isEmpty())
			return "";
		return ClassUtil.getSimpleDesc(getOldClassName(type));
	}

	private String convertMultipleTypesToByteCode(String types) {
		if (types.isEmpty())
			return "";
		String ret = "";
		for (String s : types.split(",")) {
			ret += convertTypeToByteCode(s);
		}
		return ret;
	}
}

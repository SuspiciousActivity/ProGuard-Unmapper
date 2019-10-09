package me.Unmapper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class Main implements Opcodes {

	private static HashMap<JarEntry, byte[]> OTHER_FILES = new HashMap<>();
	private static ArrayList<ClassNode> classNodes = new ArrayList<>();
	private static MappingReader mapping;

	public static boolean removeLocals = false;

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("Syntax: input.jar mapping.txt output.jar (-l)");
			System.out.println("\t-l\tRemoves local variables which are not ASCII");
			System.out.println("\t\t\t(let the decompilers generate better names)");
			System.exit(1);
		}
		if (args.length > 3) {
			removeLocals = args[3].equalsIgnoreCase("-l");
			System.out.println("Enabled local variable removal.");
		}
		load(new File(args[0]));
		map(new File(args[1]));
		save(new File(args[2]));
	}

	public static void map(File mappingFile) {
		System.out.println("Reading mapping...");
		mapping = new MappingReader(mappingFile);
		try {
			mapping.load();
		} catch (Exception ex) {
			new Exception("Error loading mapping", ex).printStackTrace();
			System.exit(1);
		}
		System.out.println("Read " + mapping.getClassMappings().size() + " class mappings and "
				+ mapping.getMemberMappings().size() + " member mappings.");
		System.out.println("Remapping...");

		ClassNodeRemapper remapper = new ClassNodeRemapper(mapping.getClassMappings(), mapping.getMemberMappings());
		for (ClassNode cn : classNodes) {
			remapper.remap(cn);
		}
	}

	public static void save(File jar) {
		System.out.println("Saving...");
		try {
			try (final JarOutputStream output = new JarOutputStream(new FileOutputStream(jar))) {
				for (Entry<JarEntry, byte[]> entry : OTHER_FILES.entrySet()) {
					output.putNextEntry(entry.getKey());
					output.write(entry.getValue());
					output.closeEntry();
				}
				for (ClassNode element : classNodes) {
					ClassWriter writer = new ClassWriter(0);
					element.accept(writer);
					output.putNextEntry(new JarEntry(element.name + ".class"));
					output.write(writer.toByteArray());
					output.closeEntry();
				}
			}
		} catch (Exception ex) {
			new Exception("Error saving jar file", ex).printStackTrace();
		}
		System.out.println("Saved!");
	}

	public static void load(File file) {
		System.out.println("Loading...");
		try {
			JarFile jar = new JarFile(file);
			ArrayList<JarEntry> entrysLeft = new ArrayList<>();
			Enumeration<JarEntry> enumeration = jar.entries();
			while (enumeration.hasMoreElements()) {
				JarEntry next = enumeration.nextElement();
				byte[] data = IOUtils.toByteArray(jar.getInputStream(next));
				if (next.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(data);
					ClassNode node = new ClassNode();
					reader.accept(node, 0);

					try {
						classNodes.add(node);
					} catch (NoClassDefFoundError e) {
						entrysLeft.add(next);
					}
				} else {
					OTHER_FILES.put(new JarEntry(next.getName()), data);
				}
			}
			while (!entrysLeft.isEmpty()) {
				for (JarEntry next : (ArrayList<JarEntry>) entrysLeft.clone()) {
					byte[] data = IOUtils.toByteArray(jar.getInputStream(next));
					if (next.getName().endsWith(".class")) {
						ClassReader reader = new ClassReader(data);
						ClassNode node = new ClassNode();
						reader.accept(node, 0);
						try {
							classNodes.add(node);
							entrysLeft.remove(next);
						} catch (NoClassDefFoundError e) {

						}
					} else {
						OTHER_FILES.put(new JarEntry(next.getName()), data);
					}
				}
			}
			jar.close();
		} catch (Exception ex) {
			new Exception("Error loading jar file", ex).printStackTrace();
		}
		System.out.println("Loaded!");
	}

}

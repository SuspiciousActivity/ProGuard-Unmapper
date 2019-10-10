package me.Unmapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ClassNodeRemapper {

	private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("(L[^;<]+[;<])");

	private final HashMap<String, String> classMappings;
	private final HashMap<MemberData, String> memberMappings;

	public ClassNodeRemapper(HashMap<String, String> classMappings, HashMap<MemberData, String> memberMappings) {
		this.classMappings = classMappings;
		this.memberMappings = memberMappings;
	}

	public void remap(ClassNode cn) {
		remapMethods(cn);
		remapFields(cn);
		cn.name = getOldClassName(cn.name);
		cn.superName = getOldClassName(cn.superName);
		cn.signature = getOldSignature(cn.signature);
		if (!cn.interfaces.isEmpty()) {
			List<String> newInterfaces = new ArrayList<String>();
			for (String s : cn.interfaces) {
				newInterfaces.add(getOldClassName(s));
			}
			cn.interfaces = newInterfaces;
		}
		if (cn.innerClasses != null && !cn.innerClasses.isEmpty()) {
			for (InnerClassNode icn : cn.innerClasses) {
				String full = getOldClassName(icn.name);
				if (icn.outerName != null) {
					String[] split = full.split("\\$");
					icn.outerName = split[0];
					if (icn.innerName != null) {
						icn.innerName = split[1];
					}
				}
				icn.name = full;
			}
		}
	}

	private static final int ACC_STATIC = 0x0008;

	private void remapMethods(ClassNode cn) {
		if (cn.methods == null)
			return;
		String clazz = cn.name;
		for (MethodNode mn : cn.methods) {
			MemberData data = new MemberData(cn.name, mn.desc, mn.name);
			mn.name = getOldMemberName(data);
			mn.desc = getOldMemberMethodDesc(mn.desc);
			mn.signature = getOldSignature(mn.signature);
			if (!mn.exceptions.isEmpty()) {
				List<String> newExceptions = new ArrayList<String>();
				for (String s : mn.exceptions) {
					newExceptions.add(getOldClassName(s));
				}
				mn.exceptions = newExceptions;
			}
			if (mn.localVariables != null && !mn.localVariables.isEmpty()) {
				Iterator<LocalVariableNode> lvns = mn.localVariables.iterator();
				while (lvns.hasNext()) {
					LocalVariableNode lvn = lvns.next();
					if (Main.removeLocals) {
						if ((mn.access & ACC_STATIC) == 0 && lvn.index == 0) { // "this" for non-static methods
							lvn.name = "this"; // should always be "this"
						} else if (!isAscii(lvn.name)) {
							lvns.remove();
							continue;
						}
					}
					lvn.desc = getOldMemberFieldDesc(lvn.desc);
					lvn.signature = getOldSignature(lvn.signature);
				}
			}
			if (mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty()) {
				for (TryCatchBlockNode tcbn : mn.tryCatchBlocks) {
					tcbn.type = getOldClassName(tcbn.type);
				}
			}
			int len = mn.instructions.size();
			for (int i = 0; i < len; i++) {
				remapInstruction(clazz, mn.instructions.get(i));
			}
		}
	}

	private void remapFields(ClassNode cn) {
		if (cn.fields == null)
			return;
		for (FieldNode fn : cn.fields) {
			MemberData data = new MemberData(cn.name, fn.desc, fn.name);
			fn.name = getOldMemberName(data);
			fn.desc = getOldMemberFieldDesc(fn.desc);
			fn.signature = getOldSignature(fn.signature);
		}
	}

	private boolean isAscii(String s) {
		for (char c : s.toCharArray()) {
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')))
				return false;
		}
		return true;
	}

	private String getOldClassName(String name) {
		if (name == null)
			return null;
		String old = classMappings.get(name);
		if (old != null)
			return old;
		return name;
	}

	private String getOldMemberName(MemberData data) {
		if (data == null)
			return null;
		String old = memberMappings.get(data);
		if (old != null)
			return old;
		return data.getName();
	}

	private String getOldMemberMethodDesc(String desc) {
		if (desc == null)
			return null;
		int argsEndIdx = desc.indexOf(')');
		String retType = getOldMemberFieldDesc(desc.substring(argsEndIdx + 1));
		String args = desc.substring(1, argsEndIdx);

		int idx = 0;
		char[] arr = args.toCharArray();
		StringBuilder outerSb = new StringBuilder("(");

		while (idx < arr.length) {
			int arrayCount = 0;
			for (; idx < arr.length && arr[idx] == '['; idx++)
				arrayCount++;

			if (idx >= arr.length) // weird input?
				return desc;

			StringBuilder sb = new StringBuilder();

			switch (arr[idx]) {
			case 'B':
			case 'Z':
			case 'I':
			case 'J':
			case 'C':
			case 'D':
			case 'F':
			case 'S':
			case 'V':
				sb.append(arr[idx]);
				idx++;
				break;
			case 'L': {
				idx++;
				int startIdx = idx;
				for (; idx < arr.length && arr[idx] != ';'; idx++)
					;
				sb.append('L');
				sb.append(getOldClassName(args.substring(startIdx, idx)));
				sb.append(';');
				idx++;
				break;
			}
			default:
				break;
			}

			for (int i = 0; i < arrayCount; i++)
				sb.insert(0, '[');

			outerSb.append(sb);
		}

		outerSb.append(')');
		outerSb.append(retType);

		return outerSb.toString();
	}

	private String getOldMemberFieldDesc(String desc) {
		if (desc == null)
			return null;
		int idx = 0;
		char[] arr = desc.toCharArray();

		int arrayCount = 0;
		for (; idx < arr.length && arr[idx] == '['; idx++)
			arrayCount++;

		if (idx >= arr.length) // weird input?
			return desc;

		StringBuilder sb = new StringBuilder();

		switch (arr[idx]) {
		case 'B':
		case 'Z':
		case 'I':
		case 'J':
		case 'C':
		case 'D':
		case 'F':
		case 'S':
		case 'V':
			sb.append(arr[idx]);
			break;
		case 'L': {
			idx++;
			int startIdx = idx;
			for (; idx < arr.length && arr[idx] != ';'; idx++)
				;
			sb.append('L');
			sb.append(getOldClassName(desc.substring(startIdx, idx)));
			sb.append(';');
			break;
		}
		default:
			break;
		}

		for (int i = 0; i < arrayCount; i++)
			sb.insert(0, '[');

		return sb.toString();
	}

	private String getOldSignature(String s) {
		if (s == null || s.isEmpty())
			return s;
		if (s.startsWith("(")) { // Methods
			int startIdx = s.indexOf('(');
			int endIdx = s.indexOf(')');
			if (endIdx < 0) // Weird?
				return s;
			String typeType = s.substring(0, startIdx);
			String args = s.substring(startIdx + 1, endIdx);
			String retType = s.substring(endIdx + 1);
			return getOldSignature(typeType) + "(" + getOldSignature(args) + ")" + getOldSignature(retType);
		} else {
			StringBuffer sb = new StringBuffer();
			Matcher m = CLASS_NAME_PATTERN.matcher(s);
			while (m.find()) {
				m.appendReplacement(sb, Matcher.quoteReplacement(getOldSignatureFromMatcher(m.group())));
			}
			m.appendTail(sb);
			return sb.toString();
		}
	}

	private String getOldSignatureFromMatcher(String s) {
		boolean endsWithBracket = s.endsWith("<");
		s = s.substring(1, s.length() - 1);
		return "L" + getOldClassName(s) + (endsWithBracket ? "<" : ";");
	}

	private void remapInstruction(String clazz, AbstractInsnNode n) {
		if (FieldInsnNode.class.isInstance(n)) {
			FieldInsnNode node = (FieldInsnNode) n;
			node.name = getOldMemberName(new MemberData(node.owner, node.desc, node.name));
			node.desc = getOldMemberFieldDesc(node.desc);
			node.owner = getOldClassName(node.owner);
		} else if (FrameNode.class.isInstance(n)) {
			FrameNode node = (FrameNode) n;
			if (node.local != null) {
				int len = node.local.size();
				for (int i = 0; i < len; i++) {
					Object o = node.local.get(i);
					if (o == null)
						continue;
					if (o instanceof String) {
						String s = (String) o;
						if (s.startsWith("["))
							s = getOldMemberFieldDesc(s);
						else
							s = getOldClassName(s);
						node.local.set(i, s);
					}
				}
			}
			if (node.stack != null) {
				int len = node.stack.size();
				for (int i = 0; i < len; i++) {
					Object o = node.stack.get(i);
					if (o == null)
						continue;
					if (o instanceof String) {
						String s = (String) o;
						if (s.startsWith("["))
							s = getOldMemberFieldDesc(s);
						else
							s = getOldClassName(s);
						node.stack.set(i, s);
					}
				}
			}
		} else if (InvokeDynamicInsnNode.class.isInstance(n)) {
			InvokeDynamicInsnNode node = (InvokeDynamicInsnNode) n;
			node.name = getOldMemberName(new MemberData(clazz, node.desc, node.name));
			node.desc = getOldMemberMethodDesc(node.desc);
			node.bsm = new Handle(node.bsm.getTag(), getOldClassName(node.bsm.getOwner()),
					getOldMemberName(new MemberData(node.bsm.getOwner(), node.bsm.getDesc(), node.bsm.getName())),
					getOldMemberMethodDesc(node.bsm.getDesc()), node.bsm.isInterface());
			int len = node.bsmArgs.length;
			for (int i = 0; i < len; i++) {
				Object o = node.bsmArgs[i];
				if (o instanceof Type) {
					Type type = (Type) o;
					try {
						Field f_off = Type.class.getDeclaredField("off");
						f_off.setAccessible(true);
						Field f_len = Type.class.getDeclaredField("len");
						f_len.setAccessible(true);
						Field f_buf = Type.class.getDeclaredField("buf");
						f_buf.setAccessible(true);
						String buf = new String((char[]) f_buf.get(type));
						String oldBuf = getOldClassName(buf);
						f_buf.set(type, oldBuf.toCharArray());
						f_len.set(type, oldBuf.length());
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else if (o instanceof Handle) {
					Handle h = (Handle) o;
					node.bsmArgs[i] = new Handle(h.getTag(), getOldClassName(h.getOwner()),
							getOldMemberName(new MemberData(h.getOwner(), h.getDesc(), h.getName())),
							getOldMemberMethodDesc(h.getDesc()), h.isInterface());
				}
			}
		} else if (MethodInsnNode.class.isInstance(n)) {
			MethodInsnNode node = (MethodInsnNode) n;
			node.name = getOldMemberName(new MemberData(node.owner, node.desc, node.name));
			node.desc = getOldMemberMethodDesc(node.desc);
			node.owner = getOldClassName(node.owner);
		} else if (TypeInsnNode.class.isInstance(n)) {
			TypeInsnNode node = (TypeInsnNode) n;
			node.desc = getOldClassName(node.desc);
		} else if (MultiANewArrayInsnNode.class.isInstance(n)) {
			MultiANewArrayInsnNode node = (MultiANewArrayInsnNode) n;
			node.desc = getOldClassName(node.desc);
		} else if (LdcInsnNode.class.isInstance(n)) {
			LdcInsnNode node = (LdcInsnNode) n;
			if (node.cst instanceof Type) {
				Type type = (Type) node.cst;
				try {
					Field f_off = Type.class.getDeclaredField("off");
					f_off.setAccessible(true);
					Field f_len = Type.class.getDeclaredField("len");
					f_len.setAccessible(true);
					Field f_buf = Type.class.getDeclaredField("buf");
					f_buf.setAccessible(true);
					String buf = new String((char[]) f_buf.get(type));
					String oldBuf = getOldClassName(buf);
					f_buf.set(type, oldBuf.toCharArray());
					f_len.set(type, oldBuf.length());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

}

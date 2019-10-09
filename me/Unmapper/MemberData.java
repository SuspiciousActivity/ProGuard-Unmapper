package me.Unmapper;

public class MemberData {

	private final String clazz;
	private final String desc;
	private final String name;

	public MemberData(String clazz, String desc, String name) {
		this.clazz = clazz;
		this.desc = desc;
		this.name = name;
	}

	public String getClazz() {
		return clazz;
	}

	public String getDesc() {
		return desc;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
		result = prime * result + ((desc == null) ? 0 : desc.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MemberData other = (MemberData) obj;
		if (clazz == null) {
			if (other.clazz != null)
				return false;
		} else if (!clazz.equals(other.clazz))
			return false;
		if (desc == null) {
			if (other.desc != null)
				return false;
		} else if (!desc.equals(other.desc))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MemberData [clazz=" + clazz + ", desc=" + desc + ", name=" + name + "]";
	}

}

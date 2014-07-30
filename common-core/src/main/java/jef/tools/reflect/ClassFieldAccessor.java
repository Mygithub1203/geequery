package jef.tools.reflect;

import java.util.HashMap;
import java.util.Map;

public class ClassFieldAccessor {
	private final Map<String,FieldAccessor> data=new HashMap<String,FieldAccessor>();
	public FieldAccessor get(String fieldName){
		return data.get(fieldName);
	}
	public synchronized void put(String fieldName,FieldAccessor accessor){
		data.put(fieldName, accessor);
	}
}

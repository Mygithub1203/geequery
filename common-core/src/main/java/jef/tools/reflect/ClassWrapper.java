/*
 * JEF - Copyright 2009-2010 Jiyi (mr.jiyi@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jef.tools.reflect;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.management.ReflectionException;

import jef.common.log.LogUtil;
import jef.tools.Assert;
import jef.tools.StringUtils;
import jef.tools.collection.CollectionUtil;
import jef.tools.collection.IterableAccessor;
import jef.tools.reflect.BeanUtils.SearchMode;

import org.apache.commons.lang.ObjectUtils;

public class ClassWrapper {
	private Class<?> cls;
	private Type     genericType;
	private ClassWrapper instanceClz;
	
	public ClassWrapper(Type type) {
		this.genericType=type;
		this.cls = GenericUtils.getRawClass(type);
		Assert.notNull(cls);
	}
	
	public ClassWrapper(Class<?> cls) {
		if(isCGLibProxy(cls)){
			cls=cls.getSuperclass();
		}
		this.genericType=cls;
		this.cls = cls;
	}
	/**
	 * 得到被包装的类型本身
	 * @return
	 */
	public Class<?> getWrappered(){
		return cls;
	}
	
	/**
	 * 得到被包装的泛型
	 * @return
	 */
	public Type getGenericType() {
		return genericType;
	}
	
	/**
	 * 使用带参数的构造器生成对象
	 * @param params
	 * @return
	 * @throws ReflectionException
	 */
	public Object newInstance(Object... params) throws ReflectionException{
		return BeanUtils.newInstance(cls,params);
	}
	
	/**
	 * 使用空参数构造，无视构造参数一律传null
	 */
	public Object newInstanceAnyway() throws ReflectionException{
		return BeanUtils.newInstanceAnyway(cls);
	}
	
	
	/**
	 * 调用该类的某个静态方法
	 * @param method
	 * @param params
	 * @return
	 * @throws ReflectionException
	 */
	public Object invokeStaticMethod(String method, Object... params) throws ReflectionException {
		return innerInvoke(null,method,true,params);
	}

	/**
	 * 用不带参数的构造器构造对象，然后运行指定的方法
	 * @param method
	 * @param params
	 * @return
	 * @throws ReflectionException
	 */
	public Object invokeWithNewInstance(String method, Object... params) throws ReflectionException {
		return innerInvoke(newInstanceAnyway(),method,false,params);
	}
	
	/*
	 * obj：对象 method方法名，isStatic静态方法，params:参数
	 */
	Object innerInvoke(Object obj,String method, boolean isStatic,Object... params) throws ReflectionException {
		try {
			if(obj==null && !isStatic)obj = newInstance();
			List<Class<?>> list = new ArrayList<Class<?>>();
			for (Object pobj : params) {
				list.add(pobj.getClass());
			}
			MethodEx me = BeanUtils.getCompatibleMethod(cls,method, list.toArray(new Class[list.size()]));
			if(me==null){
				NoSuchMethodException e=new NoSuchMethodException("Method:["+cls.getName()+"::" + method +"] not found with param count:"+params.length);
				throw new ReflectionException(e);
			}
			if(!Modifier.isPublic(me.getModifiers()) || !Modifier.isPublic(cls.getModifiers())){
				try{
					me.setAccessible(true);	
				}catch(SecurityException e){
					System.out.println(me.toString()+"\n"+e.getMessage());
				}
			}
			return me.invoke(obj, params);
		} catch (IllegalAccessException e) {
			throw new ReflectionException(e);
		} catch (SecurityException e) {
			throw new ReflectionException(e);
		} catch (IllegalArgumentException e) {
			throw new ReflectionException(e);
		} catch (InvocationTargetException e) {
			if(e.getCause() instanceof Exception){
				throw new ReflectionException((Exception)e.getCause());	
			}else{
				throw new ReflectionException(e);
			}
			
		}
	}

	/**
	 * 获得当前类以及其全部父类
	 */
	public static List<Class<?>> withSupers(Class<?> c){
		List<Class<?>> supers = new ArrayList<Class<?>>();
		supers.add(c);
		c = c.getSuperclass();
		while (c != null) {
			supers.add(c);
			c = c.getSuperclass();
		}
		return supers;
	}
	
	
	public FieldEx[] getDeclaredFields() {
		Field[] fields=cls.getDeclaredFields();
		FieldEx[] result=new FieldEx[fields.length];
		for(int i=0;i<fields.length;i++){
			result[i]=new FieldEx(fields[i],instanceClz==null?this:instanceClz);
		}
		return result;
	}
	
	/**
	 * 获得Annotation
	 */
	public <T extends Annotation> T getAnnotation(Class<T> t){
		return cls.getAnnotation(t);
	}
	
	/**
	 * 返回经过解析的泛型类型
	 * @param method
	 * @return
	 */
	public Type getMethodReturnType(Method method){
		ClassWrapper cw=this;
		method=getRealMethod(method);
		if(method.getDeclaringClass()!=this.cls){
			Type type=GenericUtils.getSuperType(null, cls, method.getDeclaringClass());
			cw=new ClassWrapper(type);
		}
		return BeanUtils.getBoundType(method.getGenericReturnType(), cw);
	}

	/**
	 * 返回经过解析的泛型类型
	 * @param method
	 * @param index
	 * @return
	 */
	public Type getMethodParamType(Method method,int index){
		ClassWrapper cw=this;
		method=getRealMethod(method);
		if(method.getDeclaringClass()!=this.cls){
			Type type=GenericUtils.getSuperType(null, cls, method.getDeclaringClass());
			cw=new ClassWrapper(type);
		}
		Type[] types=method.getGenericParameterTypes();
		if(index<0 || index>types.length){
			throw new IllegalArgumentException(StringUtils.concat("the method ",method.getName()," has ",String.valueOf(types.length)," params, index=",String.valueOf(index)," is out of bound."));
		}
		return BeanUtils.getBoundType(types[index], cw);
	}
	
	private Method getRealMethod(Method method) {
		Class<?> cls=method.getDeclaringClass();
		if(isCGLibProxy(cls)){
			try {
				return cls.getSuperclass().getMethod(method.getName(), method.getParameterTypes());
			} catch (SecurityException e) {
				LogUtil.exception(e);
			} catch (NoSuchMethodException e) {
				LogUtil.exception(e);
			}
		}
		return method;
	}

	/**
	 * 返回经过泛型解析后的方法参数
	 * @param method
	 * @return
	 */
	public Type[] getMethodParamTypes(Method method){
		ClassWrapper cw=this;
		method=getRealMethod(method);
		if(method.getDeclaringClass()!=this.cls){
			Type type=GenericUtils.getSuperType(null, cls, method.getDeclaringClass());
			cw=new ClassWrapper(type);
		}
		Type[] types=method.getGenericParameterTypes();
		for(int i=0;i<types.length;i++){
			types[i]=BeanUtils.getBoundType(types[i], cw);
		}
		return types;
	}
	
	/**
	 * 排除CGLib的代理类的干扰，得到真正的class
	 * @param clz
	 * @return
	 */
	public static Class<?> getRealClass(Class<?> clz){
		if(isCGLibProxy(clz)){
			return clz.getSuperclass();
		}
		return clz;
	}
	//判断这个类是不是CGLIB处理过的类
	private static boolean isCGLibProxy(Class<?> declaringClass) {
		return(declaringClass.getName().indexOf("$$EnhancerByCGLIB$$")>-1);
	}

	/**
	 * 根据容器类型，转换数值
	 * @param value
	 * @param container
	 * @param oldValue
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static Object toProperType(Object value,ClassWrapper container,Object oldValue){
		if(value==null)return null;
		Class<?> clz=value.getClass();
		if(container.isAssignableFrom(clz)){
			if(container.isCollection()){
				return checkAndConvertCollection((Collection)value,container);	
			}else if(container.isMap()){
				return checkAndConvertMap((Map)value,container);
			}else{
				return value;
			}
		}else if(CollectionUtil.isArrayOrCollection(clz)){
			IterableAccessor<Object> accesor=new IterableAccessor<Object>(value);
			if(container.isArray()){
				return BeanUtils.toProperArrayType(accesor, container, oldValue);
			}else if(container.isCollection()){
				return BeanUtils.toProperCollectionType(accesor, container, oldValue);
			}else{
				BeanUtils.toProperType(accesor.toString(),container,oldValue);
			}
		}
		return BeanUtils.toProperType(ObjectUtils.toString(value),container,oldValue);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object checkAndConvertMap(Map rawValue, ClassWrapper container) {
		Entry<Type,Type> types=GenericUtils.getMapTypes(container.genericType);
		boolean checkKey=types.getKey()!=Object.class;
		boolean checkValue=types.getValue()!=Object.class;
		if(!checkKey && !checkValue)return rawValue;
		
		ClassWrapper tk=new ClassWrapper(types.getKey());
		ClassWrapper tv=new ClassWrapper(types.getValue());
		
		Set<Map.Entry> es=rawValue.entrySet();
		Map result;
		try {
			result = rawValue.getClass().newInstance();
		} catch (InstantiationException e1) {
			throw new IllegalArgumentException(e1);
		} catch (IllegalAccessException e1) {
			throw new IllegalArgumentException(e1);
		}
		for(Map.Entry e: es){
			Object key=e.getKey();
			Object value=e.getValue();
			if(key!=null && checkKey)
				key=toProperType(key, tk, null);
			if(value!=null && checkValue)
				value=toProperType(value, tv, null);
			result.put(key, value);
		}
		return result;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static Object checkAndConvertCollection(Collection rawValue, ClassWrapper container) {
		Type type=GenericUtils.getCollectionType(container.genericType);
		if(type==Object.class)return rawValue;
		ClassWrapper t=new ClassWrapper(type);
		Collection result;
		try {
			result = rawValue.getClass().newInstance();
		} catch (InstantiationException e1) {
			throw new IllegalArgumentException(e1);
		} catch (IllegalAccessException e1) {
			throw new IllegalArgumentException(e1);
		}
		
		for(Object e: rawValue){
			e=toProperType(e, t, null);
			result.add(e);
		}
		return result;
	}

	/**
	 * 获得class的全称
	 * @return
	 */
	public String getName() {
		return cls.getName();
	}
	
	/**
	 * 获得class的简称
	 * @return
	 */
	public String getSimpleName() {
		return cls.getSimpleName();
	}
	
	/**
	 * 获得泛型对象的名称
	 * @return
	 */
	public String getGenericName() {
		return genericType.toString();
	}
	
	/**
	 * 得到指定的方法
	 * @param name
	 * @param params
	 * @return
	 */
	public Method getPublicMethod(String name,Class<?>... params){
		try {
			return cls.getMethod(name, params);
		} catch (SecurityException e) {
			throw new IllegalArgumentException(e.getMessage());
		} catch(NoSuchMethodException e){
			return null;
		}
	}
	
	/**
	 * 根据名称获得一个方法，前提是方法没有重名。如果没有找到，会到父类中查找
	 * @param name
	 * @return
	 */
	public MethodEx getMethodByName(String name){
		MethodEx[] methods=BeanUtils.getMethodByName(this, name, 1, SearchMode.NOT_IN_SUPER_IF_FOUND);
		if(methods.length>1){
			throw new IllegalArgumentException("There are more than 1 method match the name "+name+" in class "+ cls.getName());
		}
		if(methods.length==0)return null;
		return methods[0];
		
	}

	/**
	 * 获得所有有getter和setter的field
	 * @return
	 */
	public FieldEx[] getFieldsWithGetterAndSetter(){
		return BeanUtils.getFieldsWithGetterAndSetter(this.cls,Object.class);
	}
	
	public FieldEx[] getFieldsWithGetter(){
		return BeanUtils.getFieldsWithGetter(this.cls,Object.class);
	}
	
	public FieldEx[] getFieldsWithSetter(){
		return BeanUtils.getFieldsWithSetter(this.cls,Object.class);
	}
	
	
	/**
	 * 获得所有field名称
	 * @return
	 */
	public String[] getFieldNames(){
		return BeanUtils.getFieldNames(this.cls,Object.class);
	}
	
	/**
	 * 获得所有Field
	 * @return
	 */
	public FieldEx[] getFields(){
		return BeanUtils.getFields(this.cls,Object.class,false,false);
	}
	
	/**
	 * 获得所有public方法，包括从父类继承到的
	 * @return
	 */
	public MethodEx[] getMethods() {
		return BeanUtils.getMethods(this.cls);
	}
	
	/**
	 * 得到字段类型
	 * @param name
	 * @return
	 */
	public Class<?> getFieldType(String name){
		return BeanUtils.getFieldType(cls, name);
	}
	
	/**
	 * 得到经过泛型解析的字段类型
	 * @param name
	 * @return
	 */
	public Type getFieldGenericType(String name){
		return BeanUtils.getField(this, name).getGenericType();
	}
	
	/**
	 * 得到经过泛型解析的字段类型
	 * @param name
	 * @return
	 */
	public Type getFieldGenericType(Field field){
		Assert.notNull(field);
		ClassWrapper cw=this;
		if(field.getDeclaringClass()!=this.cls){
			Type type=GenericUtils.getSuperType(null, cls, field.getDeclaringClass());
			cw=new ClassWrapper(type);
		}
		return BeanUtils.getBoundType(field.getGenericType(), cw);
	}
	
	/**
	 * 根据类型和加载器构建
	 * @param className
	 * @param loder
	 * @return
	 * @throws ReflectionException
	 */
	public static final ClassWrapper getClassWrapper(String className,ClassLoader loder)throws ReflectionException {
		try {
			if(loder==null)loder=ClassWrapper.class.getClassLoader();
			Class<?> c= loder.loadClass(className);
			return new ClassWrapper(c);
		} catch (ClassNotFoundException e) {
			throw new ReflectionException(e,"Class: " + className+" not found.");
		}
	}
	
	/**
	 * 构建
	 * @param className
	 * @return
	 * @throws ReflectionException
	 */
	public static final ClassWrapper getClassWrapper(String className)throws ReflectionException {
		return getClassWrapper(className,null);
	}

	/**
	 * 根据提供的方法名称查找第一个符合名称的方法
	 * @param name
	 * @return
	 */
	public MethodEx getFirstMethodByName(String name) {
		MethodEx[] methods=BeanUtils.getMethodByName(this, name, 1, SearchMode.NOT_IN_SUPER_IF_FOUND);
		if(methods.length>0)return methods[0];
		return null;
	}

	/*
	 * 根据指定的泛型变量，查找泛型参数实例
	 * <p>Title: getImplType</p>
	 * <p>Description: </p>
	 * @param declaration
	 * @return
	 * @see jef.tools.reflect.GenericProvider#getImplType(java.lang.reflect.TypeVariable)
	 */
	public Type getImplType(TypeVariable<?> declaration) {
		if(declaration.getGenericDeclaration()==this.cls && this.genericType instanceof ParameterizedType){
			ParameterizedType pType =(ParameterizedType)genericType;
			int n=0;
			for(TypeVariable<?> tv:cls.getTypeParameters()){
				if(tv==declaration)break;
				n++;
			}
			return pType.getActualTypeArguments()[n];
		}
		return null;
	}

	public FieldEx getField(String field) {
		return BeanUtils.getField(this, field);
	}

	public MethodEx getMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
		return new MethodEx(cls.getMethod(name, parameterTypes),this);
	}

	public MethodEx getMethodIfExist(String name,Class<?>... paramTypes){
		try {
			Method method = cls.getMethod(name, paramTypes);
			return new MethodEx(method,this);
		} catch (SecurityException e) {
			LogUtil.exception(e);
		} catch (NoSuchMethodException e) {
			LogUtil.exception(e);
		}
		return null;
	}
	
	public boolean isInterface() {
		return cls.isInterface();
	}

	public Class<?> getEnclosingClass() {
		return cls.getEnclosingClass();
	}

	public int getModifiers() {
		return cls.getModifiers();
	}

	public Object[] getEnumConstants() {
		return cls.getEnumConstants();
	}

	public MethodEx getDeclaredMethod(String name, Class<?>... parameterTypes) throws SecurityException, NoSuchMethodException {
		Method m= cls.getDeclaredMethod(name, parameterTypes);
		return new MethodEx(m,instanceClz==null?this:instanceClz);
	}

	/**
	 * 不丢失泛型的父类型获取
	 * @return
	 */
	public ClassWrapper getSuperclass() {
		Type s=cls.getGenericSuperclass();
		if(s==null)return null;
		ClassWrapper result=new ClassWrapper(s);
		if(instanceClz!=null){
			result.instanceClz=instanceClz;
		}else{
			result.instanceClz=this;
		}
		if(Throwable.class==result.getWrappered()){
			return null;
		}
		return result;
	}

	public MethodEx[] getDeclaredMethods() {
		Method[] methods=cls.getDeclaredMethods();
		MethodEx[] result=new MethodEx[methods.length];
		for(int i=0;i<methods.length;i++){
			result[i]=new MethodEx(methods[i],instanceClz==null?this:instanceClz);
		}
		return result;
	}

	public FieldEx getDeclaredField(String name) throws SecurityException, NoSuchFieldException {
		return new FieldEx(cls.getDeclaredField(name),instanceClz==null?this:instanceClz);
	}

	public Constructor<?> getDeclaredConstructor(Class<?>... params) throws SecurityException, NoSuchMethodException {
		return cls.getDeclaredConstructor(params);
	}

	@Override
	public int hashCode() {
		return genericType.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof ClassWrapper){
			return this.genericType.equals(((ClassWrapper) obj).genericType);
		}
		return false;
	}

	public Package getPackage() {
		return cls.getPackage();
	}

	public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
		return cls.isAnnotationPresent(annotationClass);
	}

	@Override
	public String toString() {
		return genericType.toString();
	}

	public InputStream getResourceAsStream(String string) {
		return cls.getResourceAsStream(string);
	}

	public ClassLoader getClassLoader() {
		return cls.getClassLoader();
	}

	public String getPackageName() {
		String s=cls.getName();
		int n=s.lastIndexOf('.');
		if(n>-1){
			return s.substring(0,n);
		}else{
			return "";
		}
	}
	
	/**
	 * 获得指定名称的class，并包装成Wrapper
	 * @param name
	 * @return 如果class获得不成功返回null
	 */
	public static ClassWrapper forName(String name){
		Class<?> c=getClass(name);
		return c==null?null:new ClassWrapper(c);
	}
	
	/**
	 * 获得指定名称的class,如果没有返回null
	 * @param name
	 * @return
	 */
	public static Class<?> getClass(String name){
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			System.err.println("Class:" + name +" not found!");
			return null;
		}
	}

	public boolean isAssignableFrom(Class<?> class1) {
		return cls.isAssignableFrom(class1);
	}
	
	public boolean isGeneric(){
		return !this.genericType.equals(cls);
	}

	public boolean isEnum() {
		return cls.isEnum();
	}

	public boolean isArray() {
		return GenericUtils.isArray(genericType);
	}

	public boolean isCollection() {
		return CollectionUtil.isCollection(genericType);
	}
	
	public boolean isMap() {
		return Map.class.isAssignableFrom(cls);
	}
	
	public Type getComponentType() {
		return CollectionUtil.getComponentType(genericType);
	}
	
	/**
	 * 返回类所实现的接口
	 * @return
	 */
	public Class<?>[] getInterfaces(){
		return cls.getInterfaces();
	}

	/**
	 * 得到该类和父类的全部接口，如果子类父类有相同的接口，只出现一次
	 * @return
	 */
	public Class<?>[] getAllInterfaces(){
		LinkedHashSet<Class<?>> intf=new LinkedHashSet<Class<?>>();
		Class<?> c=cls;
		while(c!=Object.class){
			for(Class<?> ic:c.getInterfaces()){
				intf.add(ic);	
			}
			c=c.getSuperclass();
		}
		return intf.toArray(new Class<?>[intf.size()]);
	}
}

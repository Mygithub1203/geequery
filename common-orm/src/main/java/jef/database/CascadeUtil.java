package jef.database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.CascadeType;
import javax.persistence.FetchType;

import jef.database.meta.AbstractRefField;
import jef.database.meta.ISelectProvider;
import jef.database.meta.ITableMetadata;
import jef.database.meta.JoinKey;
import jef.database.meta.JoinPath;
import jef.database.meta.MetaHolder;
import jef.database.meta.Reference;
import jef.database.query.ReferenceType;
import jef.tools.ArrayUtils;
import jef.tools.Assert;
import jef.tools.reflect.BeanWrapper;

import org.apache.commons.lang.ObjectUtils;

final class CascadeUtil {
	protected static final CascadeType[] INSERT_WITH = new CascadeType[] { CascadeType.ALL, CascadeType.PERSIST, CascadeType.MERGE };
	protected static final CascadeType[] UPDATE_WITH = new CascadeType[] { CascadeType.ALL, CascadeType.MERGE };
	protected static final CascadeType[] DELETE_WITH = new CascadeType[] { CascadeType.ALL, CascadeType.REMOVE };
	protected static final CascadeType[] SELECT_WITH = new CascadeType[] { CascadeType.ALL, CascadeType.REFRESH };

	static int deleteWithRefInTransaction(IQueryableEntity source, Session trans) throws SQLException {
		return deleteCascadeByQuery(source, trans, true, true);
	}

	// 删除单个对象或请求和其所有级联引用
	private static int deleteCascadeByQuery(IQueryableEntity source, Session trans, boolean doSelect, boolean delSubFirst) throws SQLException {
		ITableMetadata meta = MetaHolder.getMeta(source);
		List<Reference> delrefs = new ArrayList<Reference>();
		for (AbstractRefField f : meta.getRefFieldsByName().values()) {
			if (f.isSingleColumn())
				continue;
			if (!ArrayUtils.fastContainsAny(f.getCascade(), DELETE_WITH)) {
				continue;
			}

			Reference ref = f.getReference();
			if (ref.getType() == ReferenceType.ONE_TO_MANY || ref.getType() == ReferenceType.ONE_TO_ONE) {
				delrefs.add(ref);
			}
		}
		if (delrefs.isEmpty()) {
			return trans.delete(source); // 没有需级联删除的引用，当做普通删除即可
		}
		// 要不要做
		List<IQueryableEntity> objs;
		if (doSelect) {
			source.getQuery().setCascade(false);
			objs = trans.select(source);
		} else {
			objs = Arrays.asList(source);
		}
		if (delSubFirst) {
			@SuppressWarnings("unused")
			int n = deleteChildren(objs, trans, delrefs);
			return trans.delete(source);
		} else {
			int n = trans.delete(source); // 先删除自身，在删除引用，防止在解析引用时出现循环引用又来删除自身
			deleteChildren(objs, trans, delrefs);
			return n;
		}
	}

	private static int deleteChildren(List<IQueryableEntity> objs, Session trans, List<Reference> refs) throws SQLException {
		int count = 0;
		for (IQueryableEntity obj : objs) {
			for (Reference ref : refs) {
				if (ref.getType() == ReferenceType.ONE_TO_MANY || ref.getType() == ReferenceType.ONE_TO_ONE) {
					BeanWrapper bean = BeanWrapper.wrap(obj);
					count += doDeleteRef(trans, bean, ref);
				}
			}
		}
		return count;
	}

	/*
	 * smartMode: 智能模式，当开启后自动忽略掉那些没有set过的property
	 */
	static void insertWithRefInTransaction(IQueryableEntity obj, Session trans, boolean smartMode) throws SQLException {
		// 在维护端操作之前
		BeanWrapper bean = BeanWrapper.wrap(obj);
		ITableMetadata meta = MetaHolder.getMeta(obj);

		for (AbstractRefField f : meta.getRefFieldsByName().values()) {
			if (f.isSingleColumn())
				continue;
			Reference ref = f.getReference();
			// 无需执行级联操作
			if (!ArrayUtils.fastContainsAny(f.getCascade(), INSERT_WITH)) {
				continue;
			}
			Object value = bean.getPropertyValue(f.getSourceField());
			if (value != null && ref.getType() == ReferenceType.MANY_TO_ONE) { // 多对一的话，提前维护子表
				doInsertRef1(trans, value, bean, ref, false);
			}
		}
		trans.insert(obj, null, smartMode);
		// 在维护端操作之后
		for (AbstractRefField f : meta.getRefFieldsByName().values()) {
			if (f.isSingleColumn())
				continue;
			Reference ref = f.getReference();
			Object value = bean.getPropertyValue(f.getSourceField());
			// 其他几种情况，维护子表
			switch (ref.getType()) {
			case ONE_TO_ONE:
				doInsertRef1(trans, value, bean, ref, true);
				break;
			case ONE_TO_MANY:
				doInsertRefN(trans, value, bean, ref);
				break;
			case MANY_TO_MANY:
				doInsertRefN(trans, value, bean, ref);
			}
		}
	}

	static int updateWithRefInTransaction(IQueryableEntity obj, Session trans) throws SQLException {
		Collection<AbstractRefField> refs = MetaHolder.getMeta(obj).getRefFieldsByName().values();
		int result = 0;
		// 在维护端操作之前
		for (AbstractRefField f : refs) {
			if (f.isSingleColumn())
				continue;
			Reference ref = f.getReference();
			// 无需执行级联操作
			if (!ArrayUtils.fastContainsAny(f.getCascade(), UPDATE_WITH)) {
				continue;
			}
			BeanWrapper bean = BeanWrapper.wrap(obj);
			Object value = bean.getPropertyValue(f.getSourceField());
			if (ref.getType() == ReferenceType.MANY_TO_ONE) { // 多对一的话，提前维护子表
				doUpdateRef1(trans, value, bean, ref, false);
			}
		}
		// 维护端操作
		if (obj.needUpdate())
			result = trans.update(obj);
		// 维护端操作之后
		for (AbstractRefField f : refs) {
			if (f.isSingleColumn())
				continue;
			Reference ref = f.getReference();
			// 无需执行级联操作
			if (!ArrayUtils.fastContainsAny(f.getCascade(), UPDATE_WITH)) {
				continue;
			}
			BeanWrapper bean = BeanWrapper.wrap(obj);
			Object value = bean.getPropertyValue(f.getSourceField());
			switch (ref.getType()) {
			case ONE_TO_MANY:
				doUpdateRefN(trans, value, bean, ref, true);
				break;
			case MANY_TO_MANY:
				doUpdateRefN(trans, value, bean, ref, false);
				break;
			case ONE_TO_ONE:
				doUpdateRef1(trans, value, bean, ref, true);
			}
		}
		return result;
	}

	// 维护插入操作的子表(对一操作，此处不分多对一还是单对一。因此有些问题。)
	/**
	 * 
	 * @param trans
	 * @param value
	 * @param bean
	 * @param ref
	 * @param reverse
	 *            反向。 （当子表先操作，父表后操作（此处一般指insert或update操作），称为正向。）
	 *            当先操作父表，后操作字表，称为反向。正向情况下，要将子表操作完成后 关联键值赋值到父表中。
	 *            反向情况下，在操作完成前，就将父表关键字值赋值给子表
	 * 
	 * @throws SQLException
	 */
	private static void doInsertRef1(Session trans, Object value, BeanWrapper bean, Reference ref, boolean reverse) throws SQLException {
		IQueryableEntity d = cast(value, ref.getTargetType());
		BeanWrapper bwSub = BeanWrapper.wrap(d);
		JoinPath jp = ref.toJoinPath();
		if (reverse) {// 反向维护关联键值
			for (JoinKey jk : jp.getJoinKeys()) {
				Object parentKey = bean.getPropertyValue(jk.getLeft().name());
				Object subKey = bwSub.getPropertyValue(jk.getRightAsField().name());
				if (!ObjectUtils.equals(parentKey, subKey)) {
					bwSub.setPropertyValue(jk.getRightAsField().name(), parentKey);
				}
			}
		}
		checkAndInsert(trans, d, true);
		if (!reverse) {// 正向维护关联键值
			for (JoinKey jk : jp.getJoinKeys()) {
				Object subKey = bwSub.getPropertyValue(jk.getRightAsField().name());
				Object parentKey = bean.getPropertyValue(jk.getLeft().name());
				if (!ObjectUtils.equals(parentKey, subKey)) {
					bean.setPropertyValue(jk.getLeft().name(), subKey);
				}
			}
		}
	}

	/**
	 * 对多关系，无论是一对多还是多对多，目前都是先插入父表，再插入子表的……因此其实都是反向模式
	 * 
	 * @param trans
	 * @param value
	 * @param bean
	 * @param ref
	 * @param reverse
	 * @throws SQLException
	 */
	private static void doInsertRefN(Session trans, Object value, BeanWrapper bean, Reference ref) throws SQLException {
		Map<String, Object> map = new HashMap<String, Object>();
		for (JoinKey jk : ref.toJoinPath().getJoinKeys()) {
			if (bean.isReadableProperty(jk.getLeft().name())) {
				Object refValue = bean.getPropertyValue(jk.getLeft().name());
				map.put(jk.getRightAsField().name(), refValue);
			}
		}
		List<? extends IQueryableEntity> list = castToList(value, ref);
		for (IQueryableEntity d : list) {
			BeanWrapper bwSub = BeanWrapper.wrap(d);
			for (Entry<String, Object> e : map.entrySet()) {
				Object newValue = e.getValue();
				Object oldValue = bwSub.getPropertyValue(e.getKey());
				if (!ObjectUtils.equals(oldValue, newValue)) {
					bwSub.setPropertyValue(e.getKey(), newValue);
				}
			}
			checkAndInsert(trans, d, true);
		}
	}

	private static int doDeleteRef(Session trans, BeanWrapper bean, Reference ref) throws SQLException {
		IQueryableEntity rObj = ref.getTargetType().newInstance();
		for (JoinKey r : ref.toJoinPath().getJoinKeys()) {
			rObj.getQuery().addCondition(r.getRightAsField(), bean.getPropertyValue(r.getLeft().name()));
		}
		return deleteCascadeByQuery(rObj, trans, true, false);
	}

	private static Map<List<?>, IQueryableEntity> doSelectRef(Session trans, BeanWrapper bean, Reference ref) throws SQLException {
		Map<List<?>, IQueryableEntity> result = new HashMap<List<?>, IQueryableEntity>();
		IQueryableEntity rObj = ref.getTargetType().newInstance();
		for (JoinKey r : ref.toJoinPath().getJoinKeys()) {
			rObj.getQuery().addCondition(r.getRightAsField(), bean.getPropertyValue(r.getLeft().name()));
		}
		List<? extends IQueryableEntity> list = trans.select(rObj);// 查出旧的引用对象
		for (IQueryableEntity e : list) {
			List<Object> key = DbUtils.getPrimaryKeyValue(e);
			Assert.notNull(key);
			result.put(key, e);
		}
		return result;// 按主键为key记录每个引用的对象
	}

	// 维护更新操作的子表
	private static void doUpdateRef1(Session trans, Object value, BeanWrapper bean, Reference ref, boolean doDelete) throws SQLException {
		if (value == null) {
			if (doDelete) {
				doDeleteRef(trans, bean, ref);
			}
			return;
		}
		IQueryableEntity d = cast(value, ref.getTargetType());
		checkAndInsert(trans, d, true);
		BeanWrapper bwSub = BeanWrapper.wrap(d);
		JoinPath jp = ref.toJoinPath();
		for (JoinKey jk : jp.getJoinKeys()) {
			Object newValue = bwSub.getPropertyValue(jk.getRightAsField().name());
			Object oldValue = bean.getPropertyValue(jk.getLeft().name());
			if (!ObjectUtils.equals(oldValue, newValue)) {
				bean.setPropertyValue(jk.getLeft().name(), newValue);
			}
		}
	}

	private static void doUpdateRefN(Session trans, Object value, BeanWrapper bean, Reference ref, boolean doDeletion) throws SQLException {
		// 2011-12-22:refactor logic, avoid to use delete-insert algorithm.
		// 2014-5-1: add rule, if refByMany then no deletion

		// 取得新旧的引用关系List
		Map<List<?>, IQueryableEntity> olds = doSelectRef(trans, bean, ref);
		// 新的引用关系
		List<? extends IQueryableEntity> list = castToList(value, ref);

		// 计算要更新要子表的字段和数值
		Map<String, Object> map = new HashMap<String, Object>();
		for (JoinKey jk : ref.toJoinPath().getJoinKeys()) {
			if (bean.isReadableProperty(jk.getLeft().name())) { // JoinKey的左边，就是主表中的值
				Object refValue = bean.getPropertyValue(jk.getLeft().name());
				map.put(jk.getRightAsField().name(), refValue); // 记录要更新到子表记录中的字段和值
			}
		}

		// 更新新的子表数据到
		for (IQueryableEntity d : list) {
			BeanWrapper bwSub = BeanWrapper.wrap(d);
			// 更新内存数据
			for (Entry<String, Object> e : map.entrySet()) {
				Object newValue = e.getValue();
				Object oldValue = bwSub.getPropertyValue(e.getKey());
				if (!ObjectUtils.equals(oldValue, newValue)) {
					bwSub.setPropertyValue(e.getKey(), newValue);
				}
			}
			// 对照旧值进行插入或更新
			List<Object> pks = DbUtils.getPrimaryKeyValue(d);
			IQueryableEntity old = null;

			if (pks != null) {
				old = olds.remove(pks);
			}
			if (old != null) {
				DbUtils.compareToUpdateMap(d, old);
				if (old.needUpdate()) {
					updateWithRefInTransaction(old, trans);
				}
			} else {
				checkAndInsert(trans, d, true);
			}
		}
		if (doDeletion) {
			// 将剩余的子表数据删掉
			for (IQueryableEntity d : olds.values()) {
				deleteCascadeByQuery(d, trans, false, true);
			}
		}
	}

	// 按主键去检查每条字表记录，有的就更新，没有的就插入
	private static void checkAndInsert(Session trans, IQueryableEntity d, boolean doUpdate) throws SQLException {
		if (d == null)
			return;
		if (DbUtils.getPrimaryKeyValue(d) != null) {
			d.getQuery().clearQuery();
			List<IQueryableEntity> oldValue = trans.select(d);
			if (oldValue.size() > 0) {// 更新
				if (doUpdate) {
					IQueryableEntity old = oldValue.get(0);
					DbUtils.compareToUpdateMap(d, old);
					if (old.needUpdate()) { 
						updateWithRefInTransaction(old, trans);
					}
				}
				return;
			}
		}
		// 插入
		insertWithRefInTransaction(d, trans, ORMConfig.getInstance().isDynamicInsert());
	}

	@SuppressWarnings("unchecked")
	private static <T extends IQueryableEntity> T cast(Object obj, ITableMetadata c) {
		if (obj == null)
			return null;
		if (c.getThisType().isAssignableFrom(obj.getClass())) {
			return (T) obj;
		} else {
			throw new ClassCastException(obj.getClass().getName() + " can not cast to " + c.getName());
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends IQueryableEntity> List<T> castToList(Object obj, Reference ref) {
		if (obj == null)
			return Collections.EMPTY_LIST;
		ITableMetadata c=ref.getTargetType();
		if (obj instanceof List<?>) {// 基于泛型擦除机制，自行校验对象
			for (Object element : (List<?>) obj) {
				if (!c.getThisType().isAssignableFrom(element.getClass())) {
					throw new IllegalArgumentException("There's a value can't cast to class:" + c);
				}
			}
			return (List<T>) obj;
		} else if (obj instanceof Collection<?>) {// 其他的Clection
			Collection<?> collection = (Collection<?>) obj;
			List<T> list = new ArrayList<T>();
			for (Object o : collection) {
				if (c.getThisType().isAssignableFrom(o.getClass())) {
					list.add((T) o);
				} else {
					throw new IllegalArgumentException("There's a value can't cast to class:" + c);
				}
			}
			return list;
		} else if (obj.getClass().isArray()) {
			List<T> list = new ArrayList<T>();
			for (Object element : (Object[]) obj) {
				if (c.getThisType().isAssignableFrom(element.getClass())) {
					list.add((T) element);
				} else {
					throw new IllegalArgumentException("There's a value can't cast to class:" + c);
				}
			}
			return list;
		} else if (obj instanceof Map) {
			@SuppressWarnings("rawtypes")
			List<T> list = new ArrayList<T>(((Map)obj).values());
			return list; 
		}
		throw new IllegalArgumentException("Unknow set class:" + obj.getClass());
	}

	public static ReverseReferenceProcessor getReverseProcessor(Reference ref) {
		List<Reference> reverses = ref.getExistReverseReference();
		if (reverses == null || reverses.isEmpty())
			return null;

		for (Iterator<Reference> iter = reverses.iterator(); iter.hasNext();) {
			// 凡是走到这里的都是 对多关系的反向关系，因此如果是 nv1才处理，nvn暂时不处理
			// 应该说只剩下一种关系，那就是 Nv1关系
			Reference reverse = iter.next();
			if (!reverse.getType().isToOne()) {
				 //== ReferenceType.MANY_TO_MANY ||== ReferenceType.ONE_TO_MANY
				iter.remove();
			}
		}
		return new ReverseReferenceProcessor(reverses);
	}

	// 填充1vsN的字段
	// 每次处理一个关系： JEF中一个关系允许有多个字段被填充
	// <T extends DataObject> is true
	//.get(entry.getKey())
	protected static <T> void fillOneVsManyReference(List<T> list, Map.Entry<Reference, List<AbstractRefField>> entry,  Map<Reference, List<Condition>> filters, Session session) throws SQLException {
		if (list.isEmpty())
			return;
		VsManyLoadTask task = new VsManyLoadTask(entry, filters);
		if (list.size() > 1000 || lazy(entry.getValue())) {// 不对超过1000个元素进行一对多填充//必须使用延迟加载
			markTask(task, list, session);
		} else {
			for (T obj : list) {
				task.process(session, obj);
			}
		}
	}

	static void markTask(LazyLoadTask task, List<?> objs, Session session) {
		if (objs.isEmpty())
			return;
		DataObject obj = (DataObject) objs.get(0);
		if (obj.lazyload != null) {
			obj.lazyload.getProcessor().register(task);
			return;
		}

		LazyLoadProcessor processor = new LazyLoadProcessor(task, session);
		for (Object o : objs) {
			DataObject dobj = (DataObject) o;
			dobj.lazyload = new LazyLoadContext(processor);
		}
	}

	static private boolean lazy(List<AbstractRefField> value) {
		if (ORMConfig.getInstance().isEnableLazyLoad()) {
			for (ISelectProvider prov : value) {
				AbstractRefField refd = (AbstractRefField) prov;
				if (refd.getFetch() == FetchType.EAGER) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}

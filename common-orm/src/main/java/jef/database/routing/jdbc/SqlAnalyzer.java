package jef.database.routing.jdbc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jef.common.Pair;
import jef.common.PairSO;
import jef.database.DbUtils;
import jef.database.Field;
import jef.database.OperateTarget;
import jef.database.annotation.PartitionFunction;
import jef.database.annotation.PartitionKey;
import jef.database.annotation.PartitionResult;
import jef.database.jsqlparser.expression.BinaryExpression;
import jef.database.jsqlparser.expression.Column;
import jef.database.jsqlparser.expression.JdbcParameter;
import jef.database.jsqlparser.expression.JpqlParameter;
import jef.database.jsqlparser.expression.Parenthesis;
import jef.database.jsqlparser.expression.operators.conditional.AndExpression;
import jef.database.jsqlparser.expression.operators.conditional.OrExpression;
import jef.database.jsqlparser.expression.operators.relational.Between;
import jef.database.jsqlparser.expression.operators.relational.EqualsTo;
import jef.database.jsqlparser.expression.operators.relational.ExpressionList;
import jef.database.jsqlparser.expression.operators.relational.GreaterThan;
import jef.database.jsqlparser.expression.operators.relational.GreaterThanEquals;
import jef.database.jsqlparser.expression.operators.relational.InExpression;
import jef.database.jsqlparser.expression.operators.relational.LikeExpression;
import jef.database.jsqlparser.expression.operators.relational.MinorThan;
import jef.database.jsqlparser.expression.operators.relational.MinorThanEquals;
import jef.database.jsqlparser.expression.operators.relational.NotEqualsTo;
import jef.database.jsqlparser.statement.delete.Delete;
import jef.database.jsqlparser.statement.insert.Insert;
import jef.database.jsqlparser.statement.select.PlainSelect;
import jef.database.jsqlparser.statement.select.Select;
import jef.database.jsqlparser.statement.select.SubSelect;
import jef.database.jsqlparser.statement.update.Update;
import jef.database.jsqlparser.visitor.Expression;
import jef.database.jsqlparser.visitor.ExpressionType;
import jef.database.jsqlparser.visitor.FromItem;
import jef.database.jsqlparser.visitor.Notable;
import jef.database.jsqlparser.visitor.SelectBody;
import jef.database.jsqlparser.visitor.SqlValue;
import jef.database.jsqlparser.visitor.Statement;
import jef.database.jsqlparser.visitor.VisitorAdapter;
import jef.database.meta.ITableMetadata;
import jef.database.meta.MetadataAdapter;
import jef.database.query.ComplexDimension;
import jef.database.query.Dimension;
import jef.database.query.RangeDimension;
import jef.database.query.RegexpDimension;
import jef.database.wrapper.populator.Mappers;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;

/**
 * 基于SQL语句的分库分表解析。主要逻辑部分
 * @author jiyi
 *
 */
public class SqlAnalyzer {
	public static SelectExecutionPlan getSelectExecutionPlan(Select sql, List<Object> value, OperateTarget db) {
		TableMetaCollector collector = new TableMetaCollector();
		sql.accept(collector);
		if(collector.get()==null)return null;
		MetadataAdapter meta=collector.get();
		if (meta == null || meta.getPartition() == null) {
			return null;
		}
		Map<Expression, Object> params = reverse(sql, value); // 参数对应关系还原
		Select select = (Select) sql;
		SelectBody body = select.getSelectBody();
		if (body instanceof PlainSelect) {
			StatementContext<PlainSelect> context=new StatementContext<PlainSelect>((PlainSelect) body,meta,params,value,db,collector.getModificationPoints());
			return getPlainSelectExePlan(context);
		} else {//已经是Union语句的暂不支持
			throw new UnsupportedOperationException();
		}
		
	}
	public static ExecutionPlan getExecutionPlan(Statement sql, List<Object> value, OperateTarget db) {
		TableMetaCollector collector = new TableMetaCollector();
		sql.accept(collector);
		if(collector.get()==null)return null;
		MetadataAdapter meta=collector.get();
		if (meta == null || meta.getPartition() == null) {
			return null;
		}
		Map<Expression, Object> params = reverse(sql, value); // 参数对应关系还原

		if (sql instanceof Insert) {
			StatementContext<Insert> context=new StatementContext<Insert>((Insert) sql,meta,params,value,db,collector.getModificationPoints());
			return getInsertExePlan(context);
		} else if (sql instanceof Update) {
			StatementContext<Update> context=new StatementContext<Update>((Update) sql,meta,params,value,db,collector.getModificationPoints());
			return getUpdateExePlan(context);
		} else if (sql instanceof Delete) {
			StatementContext<Delete> context=new StatementContext<Delete>((Delete) sql,meta,params,value,db,collector.getModificationPoints());
			return getDeleteExePlan(context);
		}
		return null;
	}

	// 将顺序的参数重新变为和JpqlParameter对应的map
	static class ParamReverser extends VisitorAdapter {
		ParamReverser(List<Object> raw) {
			this.rawParams = raw.iterator();
		}

		final Map<Expression, Object> params = new IdentityHashMap<Expression, Object>();
		private Iterator<Object> rawParams;

		@Override
		public void visit(JpqlParameter parameter) {
			int res = parameter.resolvedCount();
			if (res == 0) {
				params.put(parameter, rawParams.next());
			} else if (res > 0) {
				Object[] array = new Object[res];
				for (int i = 0; i < res; i++) {
					array[i] = rawParams.next();
				}
				params.put(parameter, array);
			}
		}
		@Override
		public void visit(JdbcParameter jdbcParameter) {
			params.put(jdbcParameter, rawParams.next());
		}
		public Map<Expression, Object> getParams() {
			return params;
		}
	}

	/*
	 * 将已经顺序排列好的参数和解析后的AST中的参数对象一一对应。
	 */
	private static Map<Expression, Object> reverse(Statement sql, List<Object> value) {
		ParamReverser p = new ParamReverser(value);
		sql.accept(p);
		return p.params;
	}

	/*
	 * 为Delete生成执行计划
	 */
	private static ExecutionPlan getDeleteExePlan(StatementContext<Delete> context) {
		DimensionCollector collector = new DimensionCollector(context.meta, context.paramsMap);
		Map<String, Dimension> val = getPartitionCondition(context.statement, collector);
		PartitionResult[] results=DbUtils.partitionUtil.toTableNames(context.meta, val, context.db.getPartitionSupport());
		DeleteExecutionPlan ex=new DeleteExecutionPlan(results,context);
		return ex;
	}


	/*
	 * 为Update生成执行计划
	 */
	private static ExecutionPlan getUpdateExePlan(StatementContext<Update> context) {
		DimensionCollector collector = new DimensionCollector(context.meta, context.paramsMap);
		Map<String, Dimension> val = getPartitionCondition(context.statement, collector);
		PartitionResult[] results=DbUtils.partitionUtil.toTableNames(context.meta, val, context.db.getPartitionSupport());
		UpdateExecutionPlan ex=new UpdateExecutionPlan(results,context);
		return ex;
	}

	/*
	 * 为Select生成执行计划
	 */
	private static SelectExecutionPlan getPlainSelectExePlan(StatementContext<PlainSelect> context) {
		DimensionCollector collector = new DimensionCollector(context.meta, context.paramsMap);
		Map<String, Dimension> val = getPartitionCondition(context.statement, collector);
		PartitionResult[] results=DbUtils.partitionUtil.toTableNames(context.meta, val, context.db.getPartitionSupport());
		SelectExecutionPlan ex=new SelectExecutionPlan(results,context);
		return ex;
	}
	
	
	/*
	 * 为Insert生成执行计划
	 */
	private static ExecutionPlan getInsertExePlan(StatementContext<Insert> context) {
		DimensionCollector collector = new DimensionCollector(context.meta, context.paramsMap);
		Map<String, Dimension> val = getPartitionCondition(context.statement, collector);
		PartitionResult[] results=DbUtils.partitionUtil.toTableNames(context.meta, val, context.db.getPartitionSupport());
		InsertExecutionPlan ex=new InsertExecutionPlan(results,context);
		return ex;
	}

	/*
	 * 收集路由维度，从Insert语句
	 */
	private static Map<String, Dimension> getPartitionCondition(Insert statement, DimensionCollector collector) {
		List<Column> cols=statement.getColumns();
		if(cols==null){
			throw new UnsupportedOperationException("the SQL must assign column names.");
		}
		if(statement.getItemsList() instanceof SubSelect){
			throw new UnsupportedOperationException("Can not support a subselect");
		}
		ExpressionList exp=(ExpressionList)statement.getItemsList();
		Map<String,Dimension> result=new HashMap<String,Dimension>();
		for(int i=0;i<exp.size();i++){
			Column c=cols.get(i);
			String field=collector.getPartitionField(c);
			if(field==null)continue;
			Object obj=collector.getAsValue(exp.get(i));
			if(obj==ObjectUtils.NULL){
				continue;
			}
			result.put(field, RangeDimension.create(obj, obj));
		}
		return result;
	}


	/*
	 * 收集路由维度（从Delete语句）
	 */
	private static Map<String, Dimension> getPartitionCondition(Delete statement, DimensionCollector collector) {
		if (statement.getWhere() != null) {
			return collector.parse(statement.getWhere());	
		}else{
			return Collections.emptyMap();
		}
	}
	
	/*
	 * 收集路由维度 (从Update语句)
	 * @param statement
	 * @param collector
	 * @return
	 */
	private static Map<String, Dimension> getPartitionCondition(Update sql, DimensionCollector collector) {
		Map<String,Dimension> result;
		if (sql.getWhere() != null) {
			result=collector.parse(sql.getWhere());	
		}else{
			result=Collections.emptyMap();
		}
		for(Pair<Column,Expression> set:sql.getSets()){
			String field=collector.getPartitionField(set.first);
			if(field==null)continue;
			if(result.get(field)!=null){
				continue;
			}
			Object value=collector.getAsValue(set.second);
			if(ObjectUtils.NULL!=value){
				result.put(field, RangeDimension.create(value, value));
			}
		}
		return result;
	}
	
	
	/*
	 * 递归实现——收集路由维度 
	 */
	private static Map<String, Dimension> getPartitionCondition(PlainSelect sql,DimensionCollector context) {
		Map<String,Dimension> result;
		if (sql.getWhere() != null) {
			result=context.parse(sql.getWhere());	
		}else{
			result=Collections.emptyMap();
		}
		FromItem from = sql.getFromItem();
		if(from instanceof SubSelect){
			Map<String,Dimension> cond=getPartitionCondition(((SubSelect) from).getSelectBody(),context);
			result=mergeAnd(result, cond);
		}
		return result;
	}

	/*
	 * 递归实现——收集路由维度 
	 */
	private static Map<String, Dimension> getPartitionCondition(SelectBody selectBody, DimensionCollector context) {
		if (selectBody instanceof PlainSelect) {
			return getPartitionCondition((PlainSelect) selectBody, context);
		} else {
			// Union 暂不支持
			throw new UnsupportedOperationException();
		}
	}

	@SuppressWarnings("rawtypes")
	static class DimensionCollector {
		private Map<Expression, Object> params;
		private final Map<String, String> columnToPartitionKey = new HashMap<String, String>();

		DimensionCollector(ITableMetadata meta, Map<Expression, Object> params) {
			this.params = params;
			for (Map.Entry<PartitionKey, PartitionFunction> key : meta.getEffectPartitionKeys()) {
				String field = key.getKey().field();
				Field fld = meta.getField(field);
				if (fld == null) {
					throw new IllegalArgumentException("The partition field [" + field + "] is not a database column.");
				}
				String columnName = meta.getColumnName(fld, Mappers.UPPER_COLUMNS, false);
				columnToPartitionKey.put(columnName, key.getKey().field());
			}
		}

		
		
		public Map<String, Dimension> parse(Expression exp) {
			PairSO<Dimension> dim = null;
			switch (exp.getType()) {
			case and: {
				AndExpression and = (AndExpression) exp;
				Map<String, Dimension> left = parse(and.getLeftExpression());
				Map<String, Dimension> right = parse(and.getRightExpression());
				return mergeAnd(left, right);
			}
			case or: {
				OrExpression or = (OrExpression) exp;
				Map<String, Dimension> left = parse(or.getLeftExpression());
				Map<String, Dimension> right = parse(or.getRightExpression());
				return mergeOr(left, right);
			}
			case parenthesis:
				Parenthesis p = (Parenthesis) exp;
				Map<String, Dimension> in = parse(p.getExpression());
				if (p.isNot()) {
					return mergeNot(in);
				}
				return in;
				// ////////////////////多维度运算结束/////////////
			case between:
				dim = process((Between) exp);
				break;
			case eq:
				dim = process((EqualsTo) exp);
				break;
			case ge:
				dim = process((GreaterThanEquals) exp);
				break;
			case gt:
				dim = process((GreaterThan) exp);
				break;
			case in:
				dim = process((InExpression) exp);
				break;
			case lt:
				dim = process((MinorThan) exp);
				break;
			case le:
				dim = process((MinorThanEquals) exp);
				break;
			case like:
				dim = process((LikeExpression) exp);
				break;
			case ne:
				dim = process((NotEqualsTo) exp);
				break;
			// /////////////////单维度运算结束////////////////
			// 不处理的类型
			case isnull:
			case complex:
			case arithmetic:
			case param:
			case value:
			default:
				break;
			}
			// 处理Not的场景
			if (dim != null && (exp instanceof Notable)) {
				boolean not = ((Notable) exp).isNot();
				if (not) {
					dim.second = dim.second.mergeNot();
				}
				return Collections.singletonMap(dim.first, dim.second);
			}
			return Collections.emptyMap();
		}

		private PairSO<Dimension> process(InExpression exp) {
			if (exp.getLeftExpression().getType() != ExpressionType.column) {
				return null;
			}
			String field = this.getPartitionField((Column) exp.getLeftExpression());
			if (field == null)
				return null;
			List<Object> values = new ArrayList<Object>();
			if (exp.getItemsList() instanceof ExpressionList) {
				for (Expression ex : ((ExpressionList) exp.getItemsList()).getExpressions()) {
					Object v = getAsValue(ex);
					if (v == ObjectUtils.NULL)
						return null;// in条件中有任意一个无法解析的表达式，则整个维度条件无效。
					if(v instanceof Object[]){
						values.addAll(Arrays.asList((Object[])v));
					}else{
						values.add(v);
					}
				}
			}
			Dimension d = ComplexDimension.create((Comparable[]) values.toArray(new Comparable[values.size()]));
			return new PairSO<Dimension>(field, d);
		}


		private PairSO<Dimension> process(EqualsTo exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				return v.<Dimension> replaceSecond(RangeDimension.create(v.second, v.second));
			}
			return null;
		}

		private PairSO<Dimension> process(NotEqualsTo exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				Dimension d = RangeDimension.create(v.second, v.second);
				return v.replaceSecond(d.mergeNot());
			}
			return null;
		}

		private PairSO<Dimension> process(LikeExpression exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				String like = String.valueOf(v.second);
				if (like.endsWith("%") && !like.startsWith("%")) {
					String base = StringUtils.substringBefore(like, "%");
					return v.<Dimension> replaceSecond(new RegexpDimension(base));
				}
			}
			return null;
		}

		private PairSO<Dimension> process(MinorThanEquals exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				return v.<Dimension> replaceSecond(RangeDimension.createCL(null, v.second));
			}
			return null;
		}

		private PairSO<Dimension> process(MinorThan exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				return v.<Dimension> replaceSecond(RangeDimension.createCC(null, v.second));
			}
			return null;
		}

		private PairSO<Dimension> process(GreaterThan exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				return v.<Dimension> replaceSecond(RangeDimension.createCC(v.second, null));
			}
			return null;
		}

		private PairSO<Dimension> process(GreaterThanEquals exp) {
			PairSO<Object> v = getFromBinaryOperate(exp);
			if (v != null) {
				return v.<Dimension> replaceSecond(RangeDimension.createLC(v.second, null));
			}
			return null;
		}

		private PairSO<Object> getFromBinaryOperate(BinaryExpression exp) {
			Column column = null;
			Expression valueExp = null;
			if (exp.getLeftExpression().getType() == ExpressionType.column) {
				column = (Column) exp.getLeftExpression();
				valueExp = exp.getRightExpression();
			}
			if (exp.getRightExpression().getType() == ExpressionType.column) {
				column = (Column) exp.getRightExpression();
				valueExp = exp.getLeftExpression();
			}
			String field = getPartitionField(column);
			if (field != null) {
				Object obj = getAsValue(valueExp);
				if (ObjectUtils.NULL != obj) {
					return new PairSO<Object>(field, obj);
				}
			}
			return null;
		}

		private PairSO<Dimension> process(Between exp) {
			if (exp.getLeftExpression().getType() == ExpressionType.column) {
				String field = getPartitionField((Column) exp.getLeftExpression());
				if (field == null)
					return null;
				Expression start = exp.getBetweenExpressionStart();
				Expression end = exp.getBetweenExpressionEnd();
				Object min = getAsValue(start);
				Object max = getAsValue(end);
				// 无效
				if (min == ObjectUtils.NULL && max == ObjectUtils.NULL) {
					return null;
				}
				if (min == ObjectUtils.NULL)
					min = null;
				if (max == ObjectUtils.NULL)
					max = null;
				return new PairSO<Dimension>(field, RangeDimension.create(min, max));
			}
			return null;
		}


		private String getPartitionField(Column column) {
			if (column == null)
				return null;
			String key = columnToPartitionKey.get(StringUtils.upperCase(column.getColumnName()));
			if (key == null)
				return null;
			return key;
		}
		

		/**
		 * 返回ObjectUtils.null表示是无效条件
		 * 
		 * @param exp
		 * @return
		 */
		private Object getAsValue(Expression exp) {
			if (exp.getType() == ExpressionType.value) {
				SqlValue value = (SqlValue) exp;
				return value.getValue();
			} else if (exp.getType() == ExpressionType.param) {
				Object value = params.get(exp);
				return value;
			}
			return ObjectUtils.NULL;
		}
	}

	// 对所有维度取非
	private static Map<String, Dimension> mergeNot(Map<String, Dimension> in) {
		Map<String, Dimension> result = new HashMap<String, Dimension>();
		for (Map.Entry<String, Dimension> d : in.entrySet()) {
			result.put(d.getKey(), d.getValue().mergeNot());
		}
		return result;
	}

	// 合并两次的维度，与
	private static Map<String, Dimension> mergeOr(Map<String, Dimension> left, Map<String, Dimension> right) {
		Map<String, Dimension> m = new HashMap<String, Dimension>(left);
		for (Map.Entry<String, Dimension> e : right.entrySet()) {
			Dimension old = m.put(e.getKey(), e.getValue());
			if (old != null) {
				m.put(e.getKey(), e.getValue().mergeOr(old));
			}
		}
		return m;
	}

	// 合并两次的维度，或
	private static Map<String, Dimension> mergeAnd(Map<String, Dimension> left, Map<String, Dimension> right) {
		Map<String, Dimension> m = new HashMap<String, Dimension>(left);
		for (Map.Entry<String, Dimension> e : right.entrySet()) {
			Dimension old = m.put(e.getKey(), e.getValue());
			if (old != null) {
				m.put(e.getKey(), e.getValue().mergeAnd(old));
			}
		}
		return m;
	}
}
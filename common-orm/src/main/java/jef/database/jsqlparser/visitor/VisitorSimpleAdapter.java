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
package jef.database.jsqlparser.visitor;

import java.util.Iterator;

import jef.common.Pair;
import jef.database.jsqlparser.expression.AllComparisonExpression;
import jef.database.jsqlparser.expression.AnyComparisonExpression;
import jef.database.jsqlparser.expression.BinaryExpression;
import jef.database.jsqlparser.expression.CaseExpression;
import jef.database.jsqlparser.expression.Column;
import jef.database.jsqlparser.expression.DateValue;
import jef.database.jsqlparser.expression.DoubleValue;
import jef.database.jsqlparser.expression.Function;
import jef.database.jsqlparser.expression.Interval;
import jef.database.jsqlparser.expression.InverseExpression;
import jef.database.jsqlparser.expression.JdbcParameter;
import jef.database.jsqlparser.expression.JpqlParameter;
import jef.database.jsqlparser.expression.LongValue;
import jef.database.jsqlparser.expression.NullValue;
import jef.database.jsqlparser.expression.Over;
import jef.database.jsqlparser.expression.Parenthesis;
import jef.database.jsqlparser.expression.StringValue;
import jef.database.jsqlparser.expression.Table;
import jef.database.jsqlparser.expression.TimeValue;
import jef.database.jsqlparser.expression.TimestampValue;
import jef.database.jsqlparser.expression.WhenClause;
import jef.database.jsqlparser.expression.operators.arithmetic.Addition;
import jef.database.jsqlparser.expression.operators.arithmetic.BitwiseAnd;
import jef.database.jsqlparser.expression.operators.arithmetic.BitwiseOr;
import jef.database.jsqlparser.expression.operators.arithmetic.BitwiseXor;
import jef.database.jsqlparser.expression.operators.arithmetic.Concat;
import jef.database.jsqlparser.expression.operators.arithmetic.Division;
import jef.database.jsqlparser.expression.operators.arithmetic.Mod;
import jef.database.jsqlparser.expression.operators.arithmetic.Multiplication;
import jef.database.jsqlparser.expression.operators.arithmetic.Subtraction;
import jef.database.jsqlparser.expression.operators.conditional.AndExpression;
import jef.database.jsqlparser.expression.operators.conditional.OrExpression;
import jef.database.jsqlparser.expression.operators.relational.Between;
import jef.database.jsqlparser.expression.operators.relational.EqualsTo;
import jef.database.jsqlparser.expression.operators.relational.ExistsExpression;
import jef.database.jsqlparser.expression.operators.relational.ExpressionList;
import jef.database.jsqlparser.expression.operators.relational.GreaterThan;
import jef.database.jsqlparser.expression.operators.relational.GreaterThanEquals;
import jef.database.jsqlparser.expression.operators.relational.InExpression;
import jef.database.jsqlparser.expression.operators.relational.IsNullExpression;
import jef.database.jsqlparser.expression.operators.relational.LikeExpression;
import jef.database.jsqlparser.expression.operators.relational.MinorThan;
import jef.database.jsqlparser.expression.operators.relational.MinorThanEquals;
import jef.database.jsqlparser.expression.operators.relational.NotEqualsTo;
import jef.database.jsqlparser.statement.create.CreateTable;
import jef.database.jsqlparser.statement.delete.Delete;
import jef.database.jsqlparser.statement.drop.Drop;
import jef.database.jsqlparser.statement.insert.Insert;
import jef.database.jsqlparser.statement.replace.Replace;
import jef.database.jsqlparser.statement.select.AllColumns;
import jef.database.jsqlparser.statement.select.AllTableColumns;
import jef.database.jsqlparser.statement.select.Join;
import jef.database.jsqlparser.statement.select.Limit;
import jef.database.jsqlparser.statement.select.OrderBy;
import jef.database.jsqlparser.statement.select.OrderByElement;
import jef.database.jsqlparser.statement.select.PlainSelect;
import jef.database.jsqlparser.statement.select.Select;
import jef.database.jsqlparser.statement.select.SelectExpressionItem;
import jef.database.jsqlparser.statement.select.StartWithExpression;
import jef.database.jsqlparser.statement.select.SubJoin;
import jef.database.jsqlparser.statement.select.SubSelect;
import jef.database.jsqlparser.statement.select.Union;
import jef.database.jsqlparser.statement.select.WithItem;
import jef.database.jsqlparser.statement.select.WithPart;
import jef.database.jsqlparser.statement.truncate.Truncate;
import jef.database.jsqlparser.statement.update.Update;

/**
 * VisitorAdapter是有状态的，因此不能用于多线程并发的场合。
 * 继承这个Adapter的类可以设计成无状态的。（前提是子类也必须做到无状态。）
 * @author jiyi
 *
 */
public class VisitorSimpleAdapter implements SelectVisitor, ExpressionVisitor, StatementVisitor, SelectItemVisitor {
	public void visit(PlainSelect plainSelect) {
		for (SelectItem s : plainSelect.getSelectItems()) {
			s.accept(this);
		}
		if(plainSelect.getFromItem()!=null){
			plainSelect.getFromItem().accept(this);
		}
		if (plainSelect.getJoins() != null) {
			for (Iterator<Join> joinsIt = plainSelect.getJoins().iterator(); joinsIt.hasNext();) {
				Join join = (Join) joinsIt.next();
				join.accept(this);
			}
		}
		if (plainSelect.getWhere() != null)
			plainSelect.getWhere().accept(this);

		if (plainSelect.getStartWith() != null)
			plainSelect.getStartWith().accept(this);

		if (plainSelect.getHaving() != null)
			plainSelect.getHaving().accept(this);

		if (plainSelect.getOrderBy() != null) {
			plainSelect.getOrderBy().accept(this);
		}
		if(plainSelect.getLimit()!=null){
			plainSelect.getLimit().accept(this);
		}
	}

	public void visit(OrderBy orderBy) {
		for (OrderByElement o : orderBy.getOrderByElements()) {
			o.accept(this);
		}
	}

	public void visit(Union union) {
		for (Iterator<PlainSelect> iter = union.getPlainSelects().iterator(); iter.hasNext();) {
			PlainSelect plainSelect = iter.next();
			visit(plainSelect);
		}
	}

	public void visit(SubSelect subSelect) {
		subSelect.getSelectBody().accept(this);
	}

	public void visit(Addition addition) {
		visitBinaryExpression(addition);
	}

	public void visit(AndExpression andExpression) {
		visitBinaryExpression(andExpression);
	}

	public void visit(Between between) {
		between.getLeftExpression().accept(this);
		between.getBetweenExpressionStart().accept(this);
		between.getBetweenExpressionEnd().accept(this);
	}

	public void visit(Division division) {
		visitBinaryExpression(division);
	}

	public void visit(Mod mod) {
		visitBinaryExpression(mod);
	}

	public void visit(DoubleValue doubleValue) {
	}

	public void visit(EqualsTo equalsTo) {
		visitBinaryExpression(equalsTo);
	}

	public void visit(Function function) {
		if (function.getParameters() != null)
			function.getParameters().accept(this);
		if (function.getOver() != null) {
			function.getOver().accept(this);
		}
	}

	public void visit(GreaterThan greaterThan) {
		visitBinaryExpression(greaterThan);
	}

	public void visit(GreaterThanEquals greaterThanEquals) {
		visitBinaryExpression(greaterThanEquals);
	}

	public void visit(InExpression inExpression) {
		if(inExpression.getLeftExpression()!=null){
		    for(Expression ex:inExpression.getLeftExpression()){
	            ex.accept(this);
	        }    
		}
		inExpression.getItemsList().accept(this);
	}

	public void visit(InverseExpression inverseExpression) {
		inverseExpression.getExpression().accept(this);
	}

	public void visit(IsNullExpression isNullExpression) {
		isNullExpression.getLeftExpression().accept(this);
	}

	public void visit(LikeExpression likeExpression) {
		visitBinaryExpression(likeExpression);
	}

	public void visit(ExistsExpression existsExpression) {
		existsExpression.getRightExpression().accept(this);
	}

	public void visit(LongValue longValue) {
	}

	public void visit(MinorThan minorThan) {
		visitBinaryExpression(minorThan);
	}

	public void visit(MinorThanEquals minorThanEquals) {
		visitBinaryExpression(minorThanEquals);
	}

	public void visit(Multiplication multiplication) {
		visitBinaryExpression(multiplication);
	}

	public void visit(NotEqualsTo notEqualsTo) {
		visitBinaryExpression(notEqualsTo);
	}

	public void visit(NullValue nullValue) {
	}

	public void visit(OrExpression orExpression) {
		visitBinaryExpression(orExpression);
	}

	public void visit(Parenthesis parenthesis) {
		parenthesis.getExpression().accept(this);
	}

	public void visit(StringValue stringValue) {
	}

	public void visit(Subtraction subtraction) {
		visitBinaryExpression(subtraction);
	}

	private void visitBinaryExpression(BinaryExpression binaryExpression) {
		binaryExpression.getLeftExpression().accept(this);
		binaryExpression.getRightExpression().accept(this);
	}

	public void visit(ExpressionList expressionList) {
		for (Iterator<Expression> iter = expressionList.getExpressions().iterator(); iter.hasNext();) {
			Expression expression = iter.next();
			expression.accept(this);
		}
	}

	public void visit(JdbcParameter jdbcParameter) {
	}

	public void visit(DateValue dateValue) {
	}

	public void visit(TimestampValue timestampValue) {
	}

	public void visit(TimeValue timeValue) {
	}

	public void visit(CaseExpression caseExpression) {
		if (caseExpression.getSwitchExpression() != null) {
			caseExpression.getSwitchExpression().accept(this);
		}
		if (caseExpression.getWhenClauses() != null) {
			for (WhenClause when : caseExpression.getWhenClauses()) {
				when.accept(this);
			}
		}
		if (caseExpression.getElseExpression() != null) {
			caseExpression.getElseExpression().accept(this);
		}
	}

	public void visit(WhenClause whenClause) {
		whenClause.getWhenExpression().accept(this);
		whenClause.getThenExpression().accept(this);
	}

	public void visit(AllComparisonExpression allComparisonExpression) {
		allComparisonExpression.GetSubSelect().getSelectBody().accept(this);
	}

	public void visit(AnyComparisonExpression anyComparisonExpression) {
		anyComparisonExpression.GetSubSelect().accept((ExpressionVisitor) this);
	}

	public void visit(SubJoin subjoin) {
		subjoin.getLeft().accept(this);
		subjoin.getJoin().accept(this);
	}

	public void visit(Concat concat) {
		visitBinaryExpression(concat);
	}

	public void visit(BitwiseAnd bitwiseAnd) {
		visitBinaryExpression(bitwiseAnd);
	}

	public void visit(BitwiseOr bitwiseOr) {
		visitBinaryExpression(bitwiseOr);
	}

	public void visit(BitwiseXor bitwiseXor) {
		visitBinaryExpression(bitwiseXor);
	}

	public void visit(Column tableColumn) {
	}

	public void visit(Table tableName) {
	}

	public void visit(Select select) {
		if(select.getWithItemsList()!=null){
			select.getWithItemsList().accept(this);
		}
		if (select.getSelectBody() != null)
			select.getSelectBody().accept(this);
	}
	

	@Override
	public void visit(WithPart withPart) {
		for(WithItem with: withPart.getWithItemsList()){
			with.accept(this);
		}
	}

	public void visit(Delete delete) {
		delete.getTable().accept(this);
		if (delete.getWhere() != null) {
			delete.getWhere().accept(this);
		}
	}

	public void visit(Update update) {
		update.getTable().accept(this);
		for (Pair<Column,Expression> pair : update.getSets()) {
			pair.first.accept(this);
			pair.second.accept(this);
		}
		if (update.getWhere() != null) {
			update.getWhere().accept(this);
		}
	}

	public void visit(Insert insert) {
		if (insert.getColumns() != null) {
			for (Column c : insert.getColumns()) {
				visit(c);
			}
		}
		insert.getTable().accept(this);
		insert.getItemsList().accept(this);
	}

	public void visit(Replace replace) {
		for (Column c : replace.getColumns()) {
			visit(c);
		}
		for (Expression ex : replace.getExpressions()) {
			ex.accept(this);
		}
		replace.getTable().accept(this);
		replace.getItemsList().accept(this);
	}

	public void visit(Drop drop) {
	}

	public void visit(Truncate truncate) {
		truncate.getTable().accept(this);
	}

	public void visit(CreateTable createTable) {
		createTable.getTable().accept(this);
	}

	public void visit(AllColumns allColumns) {
	}

	public void visit(AllTableColumns allTableColumns) {
	}

	public void visit(SelectExpressionItem selectExpressionItem) {
		selectExpressionItem.getExpression().accept(this);
	}

	public void visit(JpqlParameter parameter) {
	}

	public void visit(OrderByElement orderBy) {
		orderBy.getExpression().accept(this);
	}

	public void visit(Interval interval) {
		if(interval.getValue()!=null){
			interval.getValue().accept(this);
		}
	}

	public void visit(StartWithExpression startWithExpression) {
		Expression start = startWithExpression.getStartExpression();
		Expression connectBy = startWithExpression.getConnectExpression();
		if (start != null)
			start.accept(this);
		if (connectBy != null)
			connectBy.accept(this);
	}

	public void visit(Over over) {
		if (over.getPartition() != null) {
			for (Expression exp : over.getPartition()) {
				exp.accept(this);
			}
		}
		if (over.getOrderBy() != null) {
			over.getOrderBy().accept(this);
		}
	}

	public void visit(Join join) {
		join.getRightItem().accept(this);
		if (join.getOnExpression() != null) {
			join.getOnExpression().accept(this);
		}
		if (join.getUsingColumns() != null) {
			for (Column c : join.getUsingColumns()) {
				c.accept(this);
			}
		}
	}

	public void visit(WithItem with) {
		if(with.getWithItemList()!=null){
			for(SelectItem item:with.getWithItemList()){
				item.accept(this);
			}
		}
		if(with.getSelectBody()!=null){
			with.getSelectBody().accept(this);
		}
	}

	@Override
	public void visit(Limit limit) {
		if(limit.getOffsetJdbcParameter()!=null){
			limit.getOffsetJdbcParameter().accept(this);
		}
		if(limit.getRowCountJdbcParameter()!=null){
			limit.getRowCountJdbcParameter().accept(this);
		}
	}
}

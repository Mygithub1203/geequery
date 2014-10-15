package jef.database.dialect.type;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import jef.database.dialect.DatabaseDialect;
import jef.database.wrapper.result.IResultSet;

public class UnknownStringMapping extends AColumnMapping<String>{
	private String name;
	public UnknownStringMapping(String name) {
		this.name=name;
	}

	@Override
	public Object set(PreparedStatement st, Object value, int index, DatabaseDialect dialect) throws SQLException {
		st.setObject(index, value);
		return value;
	}

	@Override
	public int getSqlType() {
		return java.sql.Types.OTHER;
	}

	@Override
	public Object getProperObject(IResultSet rs, int n) throws SQLException {
		return String.valueOf(rs.getObject(n));
	}

	@Override
	protected String getSqlExpression(Object value, DatabaseDialect profile) {
		return super.wrapSqlStr(value.toString());
	}

	@Override
	public String toString() {
		return name+"|"+super.toString();
	}
	
	
}

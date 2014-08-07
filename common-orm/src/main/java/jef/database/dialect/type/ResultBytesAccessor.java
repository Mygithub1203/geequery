package jef.database.dialect.type;

import java.sql.SQLException;
import java.sql.Types;

import jef.database.wrapper.result.IResultSet;

public class ResultBytesAccessor implements ResultSetAccessor{

	public Object getProperObject(IResultSet rs, int n) throws SQLException {
		return rs.getBytes(n);
	}

	public boolean applyFor(int type) {
		return Types.BINARY==type || Types.BLOB==type || Types.LONGVARBINARY==type;
	}
	

}

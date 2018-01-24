package jef.database.meta.def;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.persistence.UniqueConstraint;

import jef.database.dialect.DatabaseDialect;
import jef.database.dialect.type.ColumnMapping;
import jef.database.meta.ITableMetadata;
import jef.database.meta.object.Constraint;
import jef.database.meta.object.ConstraintType;
import jef.tools.StringUtils;

/**
 * 定义一个唯一约束
 * @author Jiyi
 *
 */
public class UniqueConstraintDef {
	public String name;

	public String[] columnNames;

	public UniqueConstraintDef(UniqueConstraint unique) {
		this.name=unique.name();
		this.columnNames=unique.columnNames();
	}

	public String name() {
		return name;
	}

	public String[] columnNames() {
		return columnNames;
	}
	
	public List<String> getColumnNames(ITableMetadata meta, DatabaseDialect dialect) {
		List<String> columns=new ArrayList<String>(columnNames.length);
		for(int i=0;i<columnNames.length;i++){
			String name=columnNames[i];
			for(String s: StringUtils.split(name, ',')){//为了容错，这个很有可能配错
				ColumnMapping column=meta.findField(s);
				if(column==null){
					throw new NoSuchElementException("Field not found in entity "+meta.getName()+": "+s);
				}else{
					columns.add(column.getColumnName(dialect, true));
				}
			}
		}
		return columns;
	}
	
	public Constraint toConstraint(String tableName, ITableMetadata meta, DatabaseDialect dialect){
		Constraint con = new Constraint();
		con.setName(name);
		con.setTableName(tableName);
		con.setColumns(getColumnNames(meta, dialect));
		con.setType(ConstraintType.U);
		return con;
	}
}

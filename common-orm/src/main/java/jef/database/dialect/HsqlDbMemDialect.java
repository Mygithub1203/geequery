package jef.database.dialect;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import javax.persistence.PersistenceException;

import jef.common.log.LogUtil;
import jef.common.wrapper.IntRange;
import jef.database.ConnectInfo;
import jef.database.DbCfg;
import jef.database.DbUtils;
import jef.database.ORMConfig;
import jef.database.OperateTarget;
import jef.database.dialect.ColumnType.Char;
import jef.database.dialect.type.AutoIncrementMapping;
import jef.database.jsqlparser.parser.ParseException;
import jef.database.jsqlparser.statement.select.Select;
import jef.database.jsqlparser.statement.select.Union;
import jef.database.meta.Column;
import jef.database.meta.DbProperty;
import jef.database.meta.Feature;
import jef.database.query.Func;
import jef.database.query.Scientific;
import jef.database.query.function.CastFunction;
import jef.database.query.function.NoArgSQLFunction;
import jef.database.query.function.StandardSQLFunction;
import jef.database.query.function.TemplateFunction;
import jef.database.support.RDBMS;
import jef.tools.JefConfiguration;
import jef.tools.StringUtils;
import jef.tools.collection.CollectionUtil;

/**
 * HSQLDB的dialect
 * 
 */
public class HsqlDbMemDialect extends AbstractDialect {
	protected static final String DRIVER_CLASS = "org.hsqldb.jdbc.JDBCDriver";

	protected static final String HSQL_PAGE = " limit %next% offset %start%";

	public HsqlDbMemDialect() {
		super();
		super.loadKeywords("hsqldb_keywords.properties");
		
		features = CollectionUtil.identityHashSet();
		features.add(Feature.ONE_COLUMN_IN_SINGLE_DDL);
		features.add(Feature.COLUMN_ALTERATION_SYNTAX);
		features.add(Feature.CURSOR_ENDS_ON_INSERT_ROW);
		features.add(Feature.SUPPORT_BOOLEAN);
		features.add(Feature.NOT_FETCH_NEXT_AUTOINCREAMENTD);
		features.add(Feature.SUPPORT_SEQUENCE);
		
		if (JefConfiguration.getBoolean(DbCfg.DB_ENABLE_ROWID, false)) {
			features.add(Feature.SELECT_ROW_NUM);
		}
		
		registerNative(Func.now,"sysdate");
		registerNative(Func.current_timestamp,new NoArgSQLFunction("current_timestamp", false),"systimestamp");
		registerNative(Func.current_date,new NoArgSQLFunction("current_date", false),"curdate","today");
		registerNative(Func.current_time,new NoArgSQLFunction("current_time",false),"curtime");
		registerNative(Func.upper,"ucase");
		registerNative(Func.lower,"lcase");
		registerNative(Scientific.cot);
		registerNative(Scientific.degrees);
		registerNative(Scientific.radians);
		registerNative(Scientific.exp);
		registerNative(Scientific.ln,"log");
		registerNative(Scientific.log10);
		registerNative(Scientific.power);
		registerNative(Scientific.rand);
		registerNative(new StandardSQLFunction("to_number"));		
		
		registerNative(new StandardSQLFunction("days"));
		registerNative(new StandardSQLFunction("quarter"));
		registerNative(new StandardSQLFunction("week"));
		registerNative(new StandardSQLFunction("extract"));
		registerNative(new StandardSQLFunction("uuid"));
		
		registerAlias(Func.day, "days");
		registerNative(Func.year);
		registerNative(Func.month);
		registerNative(Func.hour);
		registerNative(Func.minute);
		registerNative(Func.second);

		registerNative(Func.decode);
		registerNative(Func.coalesce);
		registerNative(Func.nvl);
		registerNative(Func.nullif);
		registerNative(new StandardSQLFunction("ifnull"),"isnull");
		registerNative(new StandardSQLFunction("nvl2"));
		
		registerNative(new StandardSQLFunction("months_between"));
		registerNative(Func.timestampadd,"dateadd");
		registerNative(Func.timestampdiff,"datediff");
		registerNative(Func.add_months);
		registerNative(new StandardSQLFunction("date_add"));
		registerAlias(Func.adddate,"date_add");		
		registerNative(new StandardSQLFunction("date_sub"));
		registerAlias(Func.subdate,"date_sub");
		
		
		registerNative(Func.trunc,"truncate");
		registerNative(Func.mod);
		registerNative(Func.ceil,"ceiling");
		registerNative(Func.floor);
		registerNative(Func.round);
		registerNative(Func.locate);
		registerNative(Func.lpad);
		registerNative(Func.rpad);
		registerNative(Func.ltrim);
		registerNative(Func.rtrim);
		registerNative(Func.trim);
		
		registerNative(new NoArgSQLFunction("localtime",false));
		registerNative(new NoArgSQLFunction("localtimestamp",false));
		
		
		registerNative(new StandardSQLFunction("bitand"));
		registerNative(new StandardSQLFunction("bitandnot"));
		registerNative(new StandardSQLFunction("bitnot"));
		registerNative(new StandardSQLFunction("bitor"));
		registerNative(new StandardSQLFunction("bitxor"));
		
		registerNative(new StandardSQLFunction("ascii"));
		registerNative(new StandardSQLFunction("bit_length"));
		registerNative(new StandardSQLFunction("octet_length"));
		registerNative(new StandardSQLFunction("character_length"));
		registerAlias(Func.length, "character_length");
		registerCompatible(Func.lengthb,new TemplateFunction("lengthb", "bit_length(%s)/8"));
		
		registerNative(new StandardSQLFunction("char"));//int转char
		registerNative(new StandardSQLFunction("difference"));
		registerNative(new StandardSQLFunction("soundex"));
		
		registerNative(new StandardSQLFunction("length"));
		registerNative(Func.concat);
		registerNative(Func.translate);
		registerNative(Func.substring,"substr");
		registerNative(new StandardSQLFunction("concat_ws"));
		registerNative(new StandardSQLFunction("insert"));//INSERT ( <char value expr 1>, <offset>, <length>, <char value expr 2> )
		registerNative(new StandardSQLFunction("instr"));
		
		registerNative(new StandardSQLFunction("hextoraw"));
		registerNative(new StandardSQLFunction("rawtohex"));
		
		registerNative(new StandardSQLFunction("left"));
		registerNative(new StandardSQLFunction("right"));
		registerNative(new StandardSQLFunction("overlay"));
		
//		registerNative(Func.date);
		registerCompatible(Func.date, new TemplateFunction("time", "cast(%s as date)"));
		registerNative(Func.time);
		registerNative(Func.datediff);
		registerNative(Func.cast);
		
		registerNative(new StandardSQLFunction("position"));
		registerNative(new StandardSQLFunction("regexp_matches"));
		registerNative(new StandardSQLFunction("regexp_substring"));
		registerNative(new StandardSQLFunction("repeat"));
		registerNative(new StandardSQLFunction("reverse"));
		registerNative(Func.replace);
		
		registerCompatible(Func.str, new CastFunction("str","varchar(500)"));
		
		setProperty(DbProperty.ADD_COLUMN, "ADD COLUMN");
		setProperty(DbProperty.MODIFY_COLUMN, "ALTER");
		setProperty(DbProperty.DROP_COLUMN, "DROP COLUMN");
		setProperty(DbProperty.CHECK_SQL,"select 1 from (VALUES(0))");
		setProperty(DbProperty.SELECT_EXPRESSION,"select %s from (VALUES(0))");
		setProperty(DbProperty.WRAP_FOR_KEYWORD,"\"");
		setProperty(DbProperty.GET_IDENTITY_FUNCTION, "CALL IDENTITY()");
		
		typeNames.put(Types.TINYINT, "tinyint", 0);
		typeNames.put(Types.INTEGER, "integer", 0);
		typeNames.put(Types.BOOLEAN, "boolean", 0);
	}


	public RDBMS getName() {
		return RDBMS.hsqldb;
	}

	public String getDriverClass(String url) {
		return DRIVER_CLASS;
	}

	@Override
	public String generateUrl(String host, int port, String pathOrName) {
		if(StringUtils.isEmpty(host)){
			//生成内存格式的URL
			return "jdbc:hsqldb:mem:"+pathOrName;	
		}else{
			//生成形如的URL
			//jdbc:hsqldb:hsql://localhost:9001/testDbName
			if(port<=0)port=9001;
			if(!pathOrName.startsWith("/"))pathOrName="/"+pathOrName;
			return "jdbc:hsqldb:hsql://"+host+":"+port+pathOrName;
		}
	}

	public String toPageSQL(String sql, IntRange range) {
		boolean isUnion=false;
		try {
			Select select=DbUtils.parseNativeSelect(sql);
			if(select.getSelectBody() instanceof Union){
				isUnion=true;
			}
			select.getSelectBody();
		} catch (ParseException e) {
			LogUtil.exception("SqlParse Error:",e);
		}
		
		String start = String.valueOf(range.getLeastValue() - 1);
		String next = String.valueOf(range.getGreatestValue()
				- range.getLeastValue() + 1);
		String limit = StringUtils.replaceEach(HSQL_PAGE, new String[] {
				"%start%", "%next%" }, new String[] { start, next });
		return isUnion ?
				StringUtils.concat("select * from (", sql, ") tb__", limit) : sql.concat(limit);
	}

	public String toPageSQL(String sql, IntRange range,boolean isUnion) {
		String start = String.valueOf(range.getLeastValue() - 1);
		String next = String.valueOf(range.getGreatestValue()
				- range.getLeastValue() + 1);
		String limit = StringUtils.replaceEach(HSQL_PAGE, new String[] {
				"%start%", "%next%" }, new String[] { start, next });
		return isUnion ?
				StringUtils.concat("select * from (", sql, ") tb__", limit) : sql.concat(limit);
	}
	/**
	 * HSQLDB将名称统一转成大写形式
	 */
	@Override
	public String getObjectNameToUse(String name) {
		return name==null? null : name.toUpperCase();
	}
	
	public String getColumnNameToUse(String name) {
		return name==null?null:name.toUpperCase();
	}

	@Override
	protected String getComment(ColumnType.AutoIncrement column,boolean flag) {
		return "int generated by default as identity (start with 1)";
	}


//1 内存
//jdbc:hsqldb:mem:myDbName
//
//2 进程（In-Process）模式:从应用程序启动数据库。因为所有数据被写入到文件中，所以即使应用程序退出后，数据也不会被销毁。 
//jdbc:hsqldb:file:/C:/testdb/testDbName 
//jdbc:hsqldb:file:/opt/db/myDbName 
//jdbc:hsqldb:file:myDbName 
//
//
//3 远程
//jdbc:hsqldb:hsql://localhost:9001/testDbName 
	public void parseDbInfo(ConnectInfo connectInfo) {
		String url=connectInfo.getUrl();
		String lower=url.toLowerCase();
		if(lower.startsWith("jdbc:hsqldb:mem:")){
			String dbName=url.substring(16);
			connectInfo.setDbname(dbName);			
		}else if(lower.startsWith("jdbc:hsqldb:hsql:")){
			String path=url.substring(19);
			int index=path.indexOf('/');
			String hostport=path.substring(0,index);
			String dbname=path.substring(index+1);
			connectInfo.setHost(hostport);
			connectInfo.setDbname(dbname);
		}else if(lower.startsWith("jdbc:hsqldb:file:")){
			String path=url.substring(17);
			path=path.replace('\\', '/');
			connectInfo.setDbname(StringUtils.substringAfterLastIfExist(path, "/"));
		}else{
			throw new IllegalArgumentException(url);
		}
	}

	@Override
	public ColumnType getProprtMetaFromDbType(Column column) {
		int type=column.getDataTypeCode();
		if(type==Types.TINYINT || type==Types.SMALLINT || type==Types.INTEGER || type==Types.BIGINT){
			if (column.getColumnDef() != null && column.getColumnDef().startsWith("GENERATED")) {
				return new ColumnType.AutoIncrement(column.getColumnSize()/4);//moni
			} else {
				return new ColumnType.Int(column.getColumnSize()/4);
			}
		}else if("CHARACTER".equals(column.getDataType())){
			return new Char(column.getColumnSize());
		}
		return super.getProprtMetaFromDbType(column);
	}
	
	@Override
	public long getColumnAutoIncreamentValue(AutoIncrementMapping<?> mapping, OperateTarget db) {
		String tableName=mapping.getMeta().getTableName(false).toLowerCase();
		String seqname=tableName+"_"+mapping.columnName().toLowerCase()+"_seq";
		String sql=String.format("select nextval('%s') from dual" , seqname);
		if(ORMConfig.getInstance().isDebugMode()){
			LogUtil.show(sql + " | " + db.getTransactionId());
		}
		try{
			Statement st=db.createStatement();
			ResultSet rs=null;
			try{
				rs=st.executeQuery(sql);
				rs.next();
				return rs.getLong(1);
			}finally{
				DbUtils.close(rs);
				DbUtils.close(st);
			}	
		}catch (SQLException e) {
			throw new PersistenceException(e);
		}
	}
}

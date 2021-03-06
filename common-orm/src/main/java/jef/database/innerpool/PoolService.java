package jef.database.innerpool;

import java.sql.SQLException;
import java.util.Iterator;

import javax.sql.DataSource;

import org.easyframe.enterprise.spring.TransactionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jef.common.log.LogUtil;
import jef.database.DbCfg;
import jef.database.datasource.IRoutingDataSource;
import jef.database.datasource.SimpleDataSource;
import jef.tools.JefConfiguration;
import jef.tools.StringUtils;

public class PoolService {
	private static Logger log = LoggerFactory.getLogger(PoolService.class);

	/**
	 * 获得一个可用的池
	 * 
	 * @param ds
	 * @param profile
	 * @param max 当Max==0时等同于Nopool
	 * @return
	 */
	public static IUserManagedPool getPool(DataSource ds, int min,int max,TransactionMode txMode) {
		if(txMode==TransactionMode.JDBC || txMode==TransactionMode.JTA){
			max=0;
		}
		if(min==0){
			min = JefConfiguration.getInt(DbCfg.DB_CONNECTION_POOL, 3);
		}
		IUserManagedPool result;
		if (ds instanceof IRoutingDataSource) {
			IRoutingDataSource rds = (IRoutingDataSource) ds;
			result= new RoutingDummyConnectionPool(rds);
		} else if("org.springframework.jdbc.datasource.DriverManagerDataSource".equals(ds.getClass().getName())||ds instanceof SimpleDataSource){
            result= new SingleManagedConnectionPool(ds, min, max);
        } else{ 
			result= new SingleDummyConnectionPool(ds);
		}
		return result.setTransactionMode(txMode);
	}

	public static DataSource getPooledDatasource(String url, String driverClass, String user, String password, int min, int max) {
		SimpleDataSource ds = new SimpleDataSource();
		ds.setUrl(url);
		ds.setDriverClass(driverClass);
		ds.setUser(user);
		ds.setPassword(password);
		return new SingleManagedConnectionPool(ds, min, max);
	}

	/**
	 * 正常情況下，關閉時兩者應該相等
	 * 
	 * @param pollCount
	 * @param offerCount
	 */
	static void logPoolStatic(String name, long pollCount, long offerCount) {
		log.info("The connection {} poll-count:{} offer-count:{}", name, pollCount, offerCount);
	}
	
//	synchronized (pool) {
//	LogUtil.info("Checked [{}]. total:{},  invalid:{}", pool, total, invalid);
//}
	
	/**
	 * 描述连接可被检查的行为特性
	 * @author jiyi
	 *
	 */
	static interface CheckableConnection{
		/**
		 * 标记当前连接失效，在业务发生错误时，或者在检查线程检查出问题时，都可能使用此方法来标记连接失效。
		 */
		public void setInvalid();
		/**
		 * 执行检查
		 * @param 测试用SQL
		 * @return true表示连接正常，false或者抛出异常表示连接不可用
		 */
		public boolean checkValid(String testSql)throws SQLException;
		
		/**
		 * 执行检查，原先是isValid方法，但是该方法与JDBC4同名方法一致，如果一个类同时实现了两个接口。javac在编译时会出现委派不确定错误，因此更名为checkValid
		 * @param timeout
		 * @return  true表示连接正常，false或者抛出异常表示连接不可用
		 */
		public boolean checkValid(int timeout)throws SQLException;
		

		/**
		 * 是否被占用
		 * @return
		 */
		boolean isUsed();
	}

	
	/**
	 * 立刻检查
	 * 
	 * @param pool
	 * @return 无效的连接数. -1表示连接池无法进行检测
	 */
	public static int doCheck(String testSql,Iterator<? extends CheckableConnection> connectionsToCheck) {
		int invalid = 0;
		boolean useJDbcValidation = false;
		if (StringUtils.isBlank(testSql) || "jdbc4".equals(testSql)) {
			useJDbcValidation = true;
		}else if("false".equalsIgnoreCase(testSql) || "disable".equalsIgnoreCase(testSql)){
			return 0;
		}
		for (;connectionsToCheck.hasNext();) {
			CheckableConnection conn = connectionsToCheck.next();
			if (conn.isUsed()) {// 仅对空闲连接进行检查
				continue;
			}
			boolean flag = false;
			try {
				if (useJDbcValidation) {
					try{
						flag = conn.checkValid(5);
					}catch(AbstractMethodError e){ //JDBC未实现此方法
						//LogUtil.exception(e);无需输入堆栈
						LogUtil.warn("The Connection Check was disabled since the JDBC Driver doesn't support 'isValid(I)Z'"+conn.toString());
						return -1;
					}
				} else {
					flag = conn.checkValid(testSql);
				}
			} catch (SQLException e) {
				LogUtil.exception(e);
			}
			if (!flag) {
				conn.setInvalid();
				invalid++;
			}
		}
		return invalid;
	}
}

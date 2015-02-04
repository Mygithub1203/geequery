package org.easyframe.tutorial.lesson1;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import jef.database.DbClient;
import jef.database.jpa.JefEntityManagerFactory;

import org.easyframe.enterprise.spring.BaseDao;
import org.easyframe.enterprise.spring.CommonDao;
import org.easyframe.enterprise.spring.CommonDaoImpl;
import org.easyframe.tutorial.lesson1.entity.Foo;
import org.junit.Assert;
import org.junit.Test;

public class Case1 {
	/**
	 * At the very begining. 在最初的开始——
	 * 
	 * 我们现在创建一个名为Foo的实体 {@link Foo}。然后用API为这个实体建表、并实现增删改查操作。
	 * 大家平时操作ORM框架都是使用了一个Dao对象的，因此这里也使用了一个通用的DAO对象。
	 */
	@Test
	public void simpleTest() throws SQLException{
		DbClient db=new DbClient();
		JefEntityManagerFactory emf=new JefEntityManagerFactory(db);
		CommonDao dao=new CommonDaoImpl();
		//模拟Spring自动注入
		((BaseDao)dao).setEntityManagerFactory(emf);
		
		//创建表
		dao.getNoTransactionSession().dropTable(Foo.class);
		dao.getNoTransactionSession().createTable(Foo.class); 
		
		//增加记录
		Foo foo=new Foo();
		foo.setId(1);
		foo.setName("Hello,World!");
		foo.setCreated(new Date());
		dao.insert(foo);  //插入一条记录
		
		//从数据库查询这条记录
		Foo loaded=dao.loadByKey(Foo.class, "id", foo.getId());
		System.out.println(loaded.getName());
		
		//更新这条记录
		loaded.setName("EF-ORM is very simple.");
		dao.update(loaded);
		
		
		//删除这条记录
		dao.removeByKey(Foo.class, "id", foo.getId());
		List<Foo> allrecords=dao.find(new Foo());
		Assert.assertTrue(allrecords.isEmpty());
		
		//删除表
		dao.getNoTransactionSession().dropTable(Foo.class);
	}
}

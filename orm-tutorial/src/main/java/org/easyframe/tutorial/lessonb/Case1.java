package org.easyframe.tutorial.lessonb;

import java.sql.SQLException;

import jef.database.ORMConfig;
import jef.tools.ThreadUtils;

import org.easyframe.enterprise.spring.CommonDao;
import org.easyframe.tutorial.lessonb.entity.Foo;
import org.easyframe.tutorial.lessonb.entity.PojoEntity;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;


@ContextConfiguration(locations = { "classpath:spring/spring-test-case1.xml" })
public class Case1 extends AbstractJUnit4SpringContextTests{
	
	@javax.annotation.Resource
	private CommonDao commonDao;
	
	@Test
	public void test1() throws SQLException{
		commonDao.getNoTransactionSession().dropTable(Foo.class);
		commonDao.getNoTransactionSession().createTable(Foo.class);
		{
			Foo foo=new Foo();
			foo.setName("Hello!");
			commonDao.insert(foo);	
		}
		{
			Foo foo=new Foo();
			foo.setAge(3);
			foo.setName("飞");
			//update MY_FOO set age=3 where name='Hello!'
			commonDao.updateByProperty(foo, "name");
		}
		{
			Foo foo=commonDao.loadByPrimaryKey(Foo.class, 1);
			System.out.println(foo.getName());
		}
		{
			//根据ID删除
			commonDao.removeByKey(Foo.class, "id", 1);
		}
	}
	
	@Test
	public void test2() throws SQLException{
		//读取指定路径下的某H框架配置文件。 %s表示类的SimpleName。%c表示类的全名。
		ORMConfig.getInstance().setMetadataResourcePattern("hbm/%s.hbm.xml");
		commonDao.getNoTransactionSession().dropTable(PojoEntity.class);
		commonDao.getNoTransactionSession().createTable(PojoEntity.class);
		
		PojoEntity p=new PojoEntity();
		p.setName("fsdfsfs");
		
		commonDao.insert(p);
		System.out.println(p.getId());
		commonDao.insert(p);
		System.out.println(p.getId());
		commonDao.insert(p);
		System.out.println(p.getId());
		
		
		PojoEntity pojo=commonDao.load(p);
		System.out.println(pojo);
		
		pojo.setName("35677");
		commonDao.update(pojo);
		
		System.out.println("===========================");
		
		PojoEntity cond=new PojoEntity();
		cond.setId(12);
		System.out.println(commonDao.find(cond));
		commonDao.remove(cond);
	}
	
}

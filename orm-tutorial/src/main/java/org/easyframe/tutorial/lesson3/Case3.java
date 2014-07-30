package org.easyframe.tutorial.lesson3;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jef.codegen.EntityEnhancer;
import jef.common.wrapper.IntRange;
import jef.common.wrapper.Page;
import jef.database.DbClient;
import jef.database.ORMConfig;
import jef.database.QB;
import jef.database.query.Query;
import jef.tools.string.RandomData;

import org.easyframe.tutorial.lesson2.entity.Student;
import org.easyframe.tutorial.lesson2.entity.StudentToLession;
import org.junit.Test;

public class Case3 extends org.junit.Assert {
	DbClient db;

	public Case3() throws SQLException {
		new EntityEnhancer().enhance("org.easyframe.tutorial");
		db = new DbClient();
		// 准备数据时关闭调试，减少控制台信息
		ORMConfig.getInstance().setDebugMode(false);
		db.dropTable(Student.class, StudentToLession.class);
		db.createTable(Student.class, StudentToLession.class);
		prepareData(15);
		ORMConfig.getInstance().setDebugMode(true);
	}
	
	
	@Test
	public void test_IntRange() throws SQLException{
		Query<Student> q = QB.create(Student.class);
		
		int count=db.count(q);
		List<Student> results=db.select(q,new IntRange(11, 20));
		assertEquals(count-10, results.size());
	}
	
	
	@Test
	public void test_PageSelect() throws SQLException{
		Student st=new Student();
		st.getQuery().setAllRecordsCondition();
		st.getQuery().orderByAsc(Student.Field.id);

		int start=20;
		int limit=10;
		
		//10是每页大小，20是记录偏移。记录偏移从0开始。下面的语句相当于查询21~30条记录
		Page<Student> pagedata=	db.pageSelect(st, limit).setOffset(start).getPageData();
		
		System.out.println(pagedata.getTotalCount());
	}

	
	
	private void prepareData(int num) throws SQLException {
		List<Student> data = new ArrayList<Student>();
		Date old = new Date(System.currentTimeMillis() - 864000000000L);
		for (int i = 0; i < num; i++) {
			// 用随机数生成一些学生信息
			Student st = new Student();
			st.setGender(i % 2 == 0 ? "M" : "F");
			st.setName(RandomData.randomChineseName());
			st.setDateOfBirth(RandomData.randomDate(old, new Date()));
			st.setGrade(String.valueOf(RandomData.randomInteger(1, 6)));
			data.add(st);
		}
		db.batchInsert(data);

		List<StudentToLession> data2 = new ArrayList<StudentToLession>();
		for (int i = 0; i < num; i++) {
			StudentToLession sl = new StudentToLession();
			sl.setStudentId(data.get(i).getId());
			sl.setLessionId(100);
			data2.add(sl);
		}
		db.batchInsert(data2);

	}
}

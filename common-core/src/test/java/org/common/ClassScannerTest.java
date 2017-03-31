package org.common;

import java.io.File;

import jef.common.log.LogUtil;
import jef.tools.ClassScanner;
import jef.tools.resource.IResource;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class ClassScannerTest {
	@Test
	public void testMyListClass() {
		IResource[] names=ClassScanner.listClassNameInPackage((Class)null, new String[] { "org.slf4j" }, false);
//		LogUtil.show(names);
		System.out.println(names.length);
		Assert.assertTrue(names.length>0);
	}	
		
	@Test
	@Ignore
	public void testMyListClass2() {
		IResource[] names=ClassScanner.listClassNameInPackage(new File("d:/asia/target/perftest.jar"), new String[] {"com.ailk.easyframe.sdl.service"},  false);
		LogUtil.show(names);
		System.out.println(names.length);
		Assert.assertTrue(names.length>0);
	}
	
	@Test
	@Ignore
	public void testMyListClass3() {
		IResource[] names=ClassScanner.listClassNameInPackage((Class)null, new String[] { "com.ailk.easyframe.sdl.service"}, false);
		LogUtil.show(names);
		System.out.println(names.length);
		Assert.assertTrue(names.length>0);
	}
}

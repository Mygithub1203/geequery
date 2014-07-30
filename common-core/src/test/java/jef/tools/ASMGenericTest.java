package jef.tools;

import java.io.IOException;
import java.util.Map;

import jef.accelerator.asm.ClassReader;
import jef.accelerator.asm.ClassVisitor;
import jef.accelerator.asm.FieldVisitor;

import org.junit.Test;

public class ASMGenericTest {
	private Map<String,String>[] field1;
	
	@Test
	public void test1() throws IOException{
		ClassReader cl=new ClassReader("jef.tools.ASMGenericTest");
		System.out.println(cl.getClassName());
		cl.accept(new ClassVisitor() {
			@Override
			public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
				System.out.println(name);
				System.out.println(desc);
				System.out.println(signature);
				return super.visitField(access, name, desc, signature, value);
			}
			
			
			
		}, ClassReader.SKIP_CODE);
		
		
	}
	
}

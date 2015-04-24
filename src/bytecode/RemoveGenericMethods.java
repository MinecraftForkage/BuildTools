package bytecode;

import java.io.Reader;
import java.util.Arrays;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class RemoveGenericMethods extends BaseStreamingJarProcessor {
	public static void main(String[] args) {
		new RemoveGenericMethods().go(args);
	}
	
	public boolean hasConfig() {
		return false;
	}
	
	@Override
	public ClassVisitor createClassVisitor(final ClassVisitor parent) throws Exception {
		return new ClassVisitor(Opcodes.ASM5, parent) {
			
			String className;
			
			@Override
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				super.visit(version, access, name, signature, superName, interfaces);
				className = name;
			}
			
			@Override
			public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
				// Delete any method that calls the same method in the same class with a different return type (and the same arg types)
				// This could remove a lot of methods that shouldn't be removed, in theory. In practice it doesn't.
				// These method calls are probably never generated by javac in other cases.
				
				final Type caller = Type.getMethodType(desc);
				final String callerName = name;
				
				final MethodNode mn = new MethodNode(access, name, desc, signature, exceptions);
				return new MethodVisitor(Opcodes.ASM5, mn) {
					boolean deleteMethod = false;
					
					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						if(owner.equals(className) && name.equals(callerName)) {
							Type callee = Type.getMethodType(desc);
							if(Arrays.equals(caller.getArgumentTypes(), callee.getArgumentTypes())
								&& !caller.getReturnType().equals(callee.getReturnType())
								&& opcode != Opcodes.INVOKESTATIC)
								deleteMethod = true;
						}
						super.visitMethodInsn(opcode, owner, name, desc, itf);
					}
					
					@Override
					public void visitEnd() {
						super.visitEnd();
						
						if(!deleteMethod) {
							mn.accept(parent.visitMethod(access, name, desc, signature, exceptions));
						}
						//else
						//	System.err.println("Removing "+className+"/"+name+desc);
					}
				};
			}
		};
	}

	@Override
	public void loadConfig(Reader file) throws Exception {
	}
}

package net.mcforkage.ant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import decompsource.MakeInstallJSON;

public class MakeInstallJsonTask extends Task {
	private File vanilla, fml, output;
	private String version;
	
	public void setVanilla(File f) {vanilla = f;}
	public void setFml(File f) {fml = f;}
	public void setOutput(File f) {output = f;}
	public void setVersion(String s) {version = s;}
	
	@Override
	public void execute() throws BuildException {
		if(vanilla == null || fml == null || output == null || version == null)
			throw new BuildException("Required parameter not specified");
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (PrintStream out = new PrintStream(baos)) {
				MakeInstallJSON.go(vanilla, fml, version, out);
			}
			
			try (FileOutputStream out = new FileOutputStream(output)) {
				out.write(baos.toByteArray());
			}
			
		} catch(Exception e) {
			throw new BuildException(e);
		}
	}
}

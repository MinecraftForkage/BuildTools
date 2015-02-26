package net.mcforkage.ant;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class ApplyDiff2Task extends Task {
	private File infile, outfile, patch;
	
	public void setInput(File f) {infile = f;}
	public void setOutput(File f) {outfile = f;}
	public void setPatch(File f) {patch = f;}
	
	public static void main(String[] args) {
		ApplyDiff2Task t = new ApplyDiff2Task();
		t.infile = new File("../../build/bytecode-old.txt");
		t.outfile = new File("../../build/bytecode-new-patched2.txt");
		t.patch = new File("../../build/bytecode.patch2");
		t.execute();
	}
	
	@Override
	public void execute() throws BuildException {
		if(infile == null) throw new BuildException("Input file not set");
		if(outfile == null) throw new BuildException("Output file not set");
		if(patch == null) throw new BuildException("Patch file not set");
		
		List<String> inputLines = readFile(infile);
		
		try (PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outfile), StandardCharsets.UTF_8)))) {
			try (BufferedReader patch_in = new BufferedReader(new InputStreamReader(new FileInputStream(patch), StandardCharsets.UTF_8))) {
				String line;
				while((line = patch_in.readLine()) != null) {
					//System.out.println(line);
					
					if(line.startsWith("write "))
						out.println(line.substring(6));
					else if(line.startsWith("copy ")) {
						String[] parts = line.split(" ");
						int index = Integer.parseInt(parts[1]);
						int length = Integer.parseInt(parts[2]);
						for(int k = 0; k < length; k++)
							out.println(inputLines.get(index + k));
					}
				}
			}
			
		} catch(IOException e) {
			throw new BuildException(e);
		}
	}
	
	private List<String> readFile(File f) throws BuildException {
		ArrayList<String> lines = new ArrayList<>();
		
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
			
			String line;
			while((line = in.readLine()) != null) {
				if(line.endsWith("\r")) throw new BuildException("x");
				lines.add(line);
			}
			
		} catch(IOException e) {
			throw new BuildException("Error reading "+f, e);
		}
		
		lines.trimToSize();
		
		return lines;
	}
}

package net.mcforkage.ant;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import net.mcforkage.ant.compression.BitInputStream;
import net.mcforkage.ant.compression.HuffmanNode;
import net.mcforkage.ant.compression.HuffmanTable;
import net.mcforkage.ant.compression.HuffmanTreeVisitor;
import net.mcforkage.ant.compression.HuffmanNode.Leaf;
import net.mcforkage.ant.compression.HuffmanNode.Node;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class UncompressDiff2Task extends Task {
	private File infile, outfile;
	
	public void setInput(File f) {infile = f;}
	public void setOutput(File f) {outfile = f;}
	
	public static void main(String[] args) throws Exception {
		UncompressDiff2Task t = new UncompressDiff2Task();
		t.infile = new File("../../build/bytecode.patch2z");
		t.outfile = new File("../../build/bytecode.patch2.decomp");
		t.execute();
	}
	
	@Override
	public void execute() throws BuildException {
		if(infile == null) throw new BuildException("Input file not set");
		if(outfile == null) throw new BuildException("Output file not set");
		
		final int LITERAL_INDEX = -1;
		final int EOF_INDEX = -2;
		
		//HuffmanTable<String> literalTable = HuffmanTable.build(literalFreq);
		//HuffmanTable<Integer> indexTable = HuffmanTable.build(indexFreq);
		//HuffmanTable<Integer> lengthTable = HuffmanTable.build(lengthFreq);
		
		//printTable(indexTable.root);
		
		try (BitInputStream in = new BitInputStream(new BufferedInputStream(new FileInputStream(infile)))) {
			
			HuffmanTable<Character> charTable = HuffmanTable.readTable(in, Character.class);
			HuffmanTable<String> literalTable = new HuffmanTable<>(readStringHuffmanTable(in, charTable));
			HuffmanTable<Integer> indexTable = readDiffedHuffmanTable(in, 1);
			HuffmanTable<Integer> lengthTable = HuffmanTable.readTable(in, Integer.class);
			
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outfile), StandardCharsets.UTF_8))) {
				while(true) {
					int index = indexTable.read(in);
					if(index == EOF_INDEX)
						break;
					
					if(index == LITERAL_INDEX) {
						String s = literalTable.read(in);
						out.print("write ");
						out.println(s);
						
					} else {
						int length = lengthTable.read(in);
						
						out.print("copy ");
						out.print(index);
						out.print(" ");;
						out.println(length);
					}
				}
			}
		} catch(IOException e) {
			throw new BuildException(e);
		}
	}
	
	private HuffmanNode<String> readStringHuffmanTable(BitInputStream in, HuffmanTable<Character> charTable) throws IOException {
		if(in.readBit())
			return new HuffmanNode.Node<String>(readStringHuffmanTable(in, charTable), readStringHuffmanTable(in, charTable));
		
		StringBuilder val = new StringBuilder();
		while(true) {
			char ch = charTable.read(in);
			if(ch == '\uFFFE')
				break;
			val.append(ch);
		}
		
		return new HuffmanNode.Leaf<String>(val.toString(), 0);
	}
	
	private HuffmanNode<Integer> readDiffedHuffmanNode(BitInputStream in, HuffmanTable<Integer> diffTable, int[] lastValue) throws IOException {
		if(in.readBit())
			return new HuffmanNode.Node<Integer>(readDiffedHuffmanNode(in, diffTable, lastValue), readDiffedHuffmanNode(in, diffTable, lastValue));
		
		lastValue[0] += diffTable.read(in);
		return new HuffmanNode.Leaf<Integer>(lastValue[0], 0);
	}
	
	private HuffmanTable<Integer> readDiffedHuffmanTable(final BitInputStream in, int levels) throws IOException {
		
		if(levels == 0)
			return HuffmanTable.readTable(in, Integer.class);

		HuffmanTable<Integer> diffTable = readDiffedHuffmanTable(in, levels - 1);
		
		return new HuffmanTable<>(readDiffedHuffmanNode(in, diffTable, new int[] {-2}));
	}
}

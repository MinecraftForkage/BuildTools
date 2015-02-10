package net.mcforkage.ant;

import immibis.bon.com.immibis.json.JsonReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import bytecode.BaseStreamingZipProcessor;

public class DownloadLibrariesTask extends Task {
	
	private File jsonfile, libsdir, nativesdir;
	
	public void setJsonfile(File f) {
		jsonfile = f;
	}
	
	public void setLibsdir(File f) {
		libsdir = f;
	}
	
	public void setNativesdir(File f) {
		nativesdir = f;
	}
	
	@Override
	public void execute() throws BuildException {
		if(jsonfile == null)
			throw new BuildException("jsonfile not specified");
		if(libsdir == null)
			throw new BuildException("libsdir not specified");
		if(nativesdir == null)
			throw new BuildException("nativesdir not specified");
		
		String osType = "unknown";
		{
			String osname = System.getProperty("os.name").toLowerCase();
			if(osname.contains("linux") || osname.contains("unix"))
				osType = "linux";
			if(osname.contains("win"))
				osType = "windows";
			if(osname.contains("mac"))
				osType = "mac";
		}
		
		String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
		
		Map json;
		try (FileReader fr = new FileReader(jsonfile)) {
			json = (Map)JsonReader.readJSON(fr);
		} catch(IOException e) {
			throw new BuildException("Failed to read or parse "+jsonfile, e);
		}
		
		List<Map> libraries = (List<Map>)json.get("libraries");
		
		for(Map libraryObject : (List<Map>)libraries) {
			Map<String, Object> library = (Map<String, Object>)libraryObject;
			
			Object nameObject = library.get("name");
			Object urlObject = library.get("url");
			Object childrenObject = library.get("children");
			Object rulesObject = library.get("rules");
			Object nativesObject = library.get("natives");
			
			String name = (String)nameObject;
			
			if(rulesObject != null) {
				if(!checkRules((List<?>)rulesObject, osType)) {
					continue;
				}
			}
			
			List<String> suffixes = new ArrayList<>();
			
			if(nativesObject == null)
				suffixes.add("");
			else {
				String nativeSuffixObject = (String)((Map<String, ?>)nativesObject).get(osType);
				if(nativeSuffixObject == null) throw new BuildException("natives library "+name+" has no native suffix specified");
				
				suffixes.add("-" + (String)nativeSuffixObject);
			}
			
			if(childrenObject != null)
				for(String o : (List<String>)childrenObject)
					suffixes.add("-" + o);
			
			String baseURL = (urlObject != null ? (String)urlObject + "/" : "https://libraries.minecraft.net/");
			
			List<File> files = download(libsdir, name, baseURL, suffixes, arch);
			
			if(nativesObject != null) {
				extractNatives(files.get(0), nativesdir);
			}
		}
	}
	

	private static void extractNatives(File zipFile, File nativesDir) throws BuildException {
		//System.out.println("extracting natives: "+zipFile.getName());
		try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile))) {
			ZipEntry ze;
			while((ze = zin.getNextEntry()) != null) {
				if(ze.getName().endsWith("/") || ze.getName().startsWith("META-INF/")) {
					zin.closeEntry();
					continue;
				}
				
				if(ze.getName().contains("/") || ze.getName().contains(File.separator))
					throw new SecurityException("filename contains separator: "+ze.getName());
				
				File outFile = new File(nativesDir, ze.getName());
				if(!outFile.exists()) {
					try (FileOutputStream fout = new FileOutputStream(outFile)) {
						BaseStreamingZipProcessor.copyResource(zin, fout);
					}
				}
				zin.closeEntry();
			}
		} catch(IOException e) {
			throw new BuildException("Failed to extract natives from "+zipFile+" to "+nativesDir, e);
		}
	}

	private static List<File> download(File libsDir, String name, String baseURL, List<String> suffixes, String arch) throws BuildException {
		String[] nameParts = name.split(":");
		if(nameParts.length != 3)
			throw new BuildException("malformed library name: "+name);
		
		List<File> files = new ArrayList<>();
		
		for(String suffix : suffixes) {
			String fileName = nameParts[1] + "-" + nameParts[2] + suffix + ".jar";
			fileName = fileName.replace("${arch}", arch);
			if(fileName.contains("/") || fileName.contains(File.separator))
				throw new SecurityException("Filename contains separator. Filename is: "+fileName);
			String url = baseURL + nameParts[0].replace(".", "/") + "/" + nameParts[1] + "/" + nameParts[2] + "/" + fileName;
			
			files.add(new File(libsDir, fileName));
			
			
			if(new File(libsDir, fileName).exists()) {
				continue;
			}
			
			System.err.println(fileName);
			
			try {
				HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
				conn.setRequestProperty("User-Agent", "TotallyNotJavaMasqueradingAsRandomStuffBecauseForSomeReasonJavaUserAgentsAreBlacklistedButOnlyFromSomeRepositories/1.0");
				try (InputStream downloadStream = conn.getInputStream()) {
					try (OutputStream fileStream = new FileOutputStream(new File(libsDir, fileName))) {
						BaseStreamingZipProcessor.copyResource(downloadStream, fileStream);
					}
				}
			} catch(IOException e) {
				throw new BuildException("Failed to download "+url, e);
			}
		}
		
		return files;
	}

	/** Returns true if this library is allowed. */
	@SuppressWarnings("unchecked")
	private static boolean checkRules(List<?> rules, String osType) throws BuildException {
		boolean allowed = false;
		for(Object ruleObject : rules) {
			if(!(ruleObject instanceof Map)) throw new BuildException("malformed dev.json");
			
			Map<String, ?> rule = (Map<String, ?>)ruleObject;
			boolean ruleAction;
			if("allow".equals(rule.get("action")))
				ruleAction = true;
			else if("disallow".equals(rule.get("action")))
				ruleAction = false;
			else
				throw new BuildException("malformed dev.json");
			
			Map.Entry<String, ?> condition = null;
			for(Map.Entry<String, ?> entry : rule.entrySet()) {
				if(entry.getKey().equals("action"))
					continue;
				else if(condition == null)
					condition = entry;
				else
					throw new BuildException("can't handle rule with more than one condition in dev.json: conditions are "+entry.getKey()+" and "+condition.getKey());
			}
			
			if(condition == null)
				allowed = ruleAction;
			
			else if(condition.getKey().equals("os")) {
				if(!(condition.getValue() instanceof Map)) throw new BuildException("malformed dev.json");
				Map<String, ?> attrs = (Map<String, ?>)condition.getValue();
				
				if(attrs.size() != 1 || !attrs.containsKey("name")) throw new BuildException("can't handle os condition: "+attrs);
				
				if(osType.equals(attrs.get("name")))
					allowed = ruleAction;
			
			} else
				throw new BuildException("can't handle unknown rule condition "+condition.getKey());
			
		}
		return allowed;
	}
}

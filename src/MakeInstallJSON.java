import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

import misc.JsonWriter;
import immibis.bon.com.immibis.json.JsonReader;


public class MakeInstallJSON {
	public static void main(String[] args) throws Exception {
		if(args.length != 3) {
			System.err.println("Usage: java MakeInstallJSON vanilla.json fml.json OUTPUT_VERSION_NAME > output.json");
			System.exit(1);
		}
		
		Map vanilla, fml;
		
		try (FileReader fr = new FileReader(new File(args[0]))) {
			vanilla = (Map)JsonReader.readJSON(fr);
		}
		
		try (FileReader fr = new FileReader(new File(args[1]))) {
			fml = (Map)JsonReader.readJSON(fr);
		}
		
		// override the list of libraries
		vanilla.put("libraries", fml.get("libraries"));
		
		// fix library base URLs - must end with /
		for(Map library : (List<Map>)vanilla.get("libraries")) {
			if(library.containsKey("url")) {
				if(!((String)library.get("url")).endsWith("/"))
					library.put("url", library.get("url") + "/");
			}
		}
		
		// override the version ID
		vanilla.put("id", args[2]);
		
		// add FML tweak class to arguments
		vanilla.put("minecraftArguments", vanilla.get("minecraftArguments") + " --tweakClass cpw.mods.fml.common.launcher.FMLTweaker");
		
		// override main class
		vanilla.put("mainClass", "net.minecraft.launchwrapper.Launch");
		
		System.out.println(JsonWriter.toString(vanilla));
	}
}

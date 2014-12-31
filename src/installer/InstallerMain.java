package installer;

import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;

/**
 * This gets packaged together with install-data.zip.lzma,
 * and run as the main class for the installer distributed to users.
 */
public class InstallerMain {
	static class AlreadyHandledException extends Exception {}
	
	public static void main(String[] args) {
		
		File tempDir = null;
		
		ProgressDialog dlg = ProgressDialog.openModal(null, "MCF Installer");
		
		try {
			Map<String, byte[]> installData;
			
			dlg.startIndeterminate("Unpacking installer");
			
			{
				File devInstallDataFile = new File("../../build/install-data.zip.lzma");
				InputStream embeddedInstallDataStream = InstallerMain.class.getResourceAsStream("/build/install-data.zip.lzma");
				if(embeddedInstallDataStream == null)
				{
					if(devInstallDataFile.exists())
						embeddedInstallDataStream = new FileInputStream(devInstallDataFile);
					else {
						JOptionPane.showMessageDialog(null, "The install data file is missing. Whoever created this installer screwed something up.", "MCF Installer Failure", JOptionPane.ERROR_MESSAGE);
						throw new AlreadyHandledException();
					}
				}
				
				try (InputStream in = new LzmaInputStream(embeddedInstallDataStream, new Decoder())) {
					installData = Utils.readZip(in);
				}
			}

			Properties installProperties = new Properties();
			if(!installData.containsKey("install.properties")) {
				JOptionPane.showMessageDialog(null, "The install properties file is missing. Whoever created this installer screwed something up.", "MCF Installer Failure", JOptionPane.ERROR_MESSAGE);
				throw new AlreadyHandledException();
			}
			
			try (Reader reader = new InputStreamReader(new ByteArrayInputStream(installData.get("install.properties")))) {
				installProperties.load(reader);
			}
			
			String mcver = installProperties.getProperty("mcver");
			String mcfver = installProperties.getProperty("mcfver");
			String launcherVersionName = installProperties.getProperty("launcherVersionName");
			
			if(mcver == null || mcfver == null || launcherVersionName == null) {
				JOptionPane.showMessageDialog(null, "The install properties file is corrupted. Whoever created this installer screwed something up.", "MCF Installer Failure", JOptionPane.ERROR_MESSAGE);
				throw new AlreadyHandledException();
			}
			
			tempDir = File.createTempFile("MCF-INSTALLER-", ".tmp");
			if(!tempDir.delete() || !tempDir.mkdirs())
				throw new Exception("Failed to create directory "+tempDir.getAbsolutePath());
			
			System.out.println("Temp dir: "+tempDir);
			
			
			dlg.startIndeterminate("Downloading client");
			byte[] mcClient = Utils.download(dlg, "http://s3.amazonaws.com/Minecraft.Download/versions/"+mcver+"/"+mcver+".jar", "minecraft.jar");
			if(mcClient == null)
				throw new AlreadyHandledException(); // Utils.download already displayed an error message
			
			File clientFile = new File(tempDir, "client.jar");
			try (FileOutputStream out = new FileOutputStream(clientFile)) {
				out.write(mcClient);
			}
			
			dlg.startIndeterminate("Downloading server");
			byte[] mcServer = Utils.download(dlg, "http://s3.amazonaws.com/Minecraft.Download/versions/"+mcver+"/minecraft_server."+mcver+".jar", "minecraft_server.jar");
			if(mcServer == null)
				throw new AlreadyHandledException();
			
			File serverFile = new File(tempDir, "server.jar");
			try (FileOutputStream out = new FileOutputStream(serverFile)) {
				out.write(mcServer);
			}
			
			mcClient = null;
			mcServer = null;
			
			dlg.startIndeterminate("Installing");
			
			File mainJarFile = Installer.install(clientFile, serverFile, tempDir, installData, dlg);
			
			
			
			///////// INSTALL VERSION IN MINECRAFT LAUNCHER /////////
			
			File versionDir = new File(Utils.getMinecraftDirectory(), "versions/" + launcherVersionName);
			if(versionDir.exists())
				throw new Exception("Already exists: "+versionDir);
			
			versionDir.mkdirs();
			
			// Install JSON file (by copying from install data)
			try (FileOutputStream versionJsonOut = new FileOutputStream(new File(versionDir, launcherVersionName+".json"))) {
				versionJsonOut.write(installData.get("install.json"));
			}
			
			// Install JAR file (by copying from temp dir)
			try (FileOutputStream jarOut = new FileOutputStream(new File(versionDir, launcherVersionName+".jar"))) {
				try (FileInputStream jarIn = new FileInputStream(mainJarFile)) {
					Utils.copyStream(jarIn, jarOut);
				}
			}
			
			
			
			
			// Done!
			JOptionPane.showMessageDialog(dlg, "Minecraft Forkage was successfully installed.\nVersion name in launcher: "+launcherVersionName, "MCF Installer - Done!", JOptionPane.INFORMATION_MESSAGE);
			
			
		} catch(AlreadyHandledException e) {
			// do nothing
		
		} catch(Exception e) {
			e.printStackTrace(); // in case someone is running this in a console
			
			StringWriter sw = new StringWriter();
			try (PrintWriter pw = new PrintWriter(sw)) {
				e.printStackTrace(pw);
			}
			JOptionPane.showMessageDialog(null, sw.toString(), "MCF Installer - unexpected error", JOptionPane.ERROR_MESSAGE);
			
		} finally {
			dlg.setVisible(false);
		}
		
		if(tempDir != null)
			deleteRecursive(tempDir);
		
		System.exit(1);
	}

	private static void deleteRecursive(File f) {
		if(f.isDirectory())
			for(File child : f.listFiles())
				deleteRecursive(child);
		f.delete();
	}
}
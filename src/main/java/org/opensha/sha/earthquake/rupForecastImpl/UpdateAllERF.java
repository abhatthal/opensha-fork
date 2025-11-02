package org.opensha.sha.earthquake.rupForecastImpl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_Downloader;
import org.scec.getfile.GetFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import scratch.UCERF3.utils.UCERF3_Downloader;

/**
 * Test that we're able to download all ERF data using GetFile.
 * Required for testing for filesystem corruption.
 * This should only be run occassionally as updating all files may be expensive.
 */
public class UpdateAllERF {
	/**
	 * Returns a list of all key entries from the JSON
	 * @param jsonFile The metadata file listing all JSON entries
	 * @return
	 */
	private static String[] getModels(File jsonFile) {
		try (Reader reader = new FileReader(jsonFile)) {
			JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
			String[] keys = jsonObject.keySet().toArray(new String[0]);
			return keys;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Download all ERF data for a given ERF
	 * @param erfName	 Name of ERF (i.e. ucerf3, nshm23)
	 * @param downloader GetFile instance to download specific ERF
	 * @return true if downloaded successfully
	 */
	public static boolean updateERF(String erfName, GetFile downloader) {
		try {
			System.out.println("Updating all ERF data for " + erfName);
			downloader.updateAll().get();
			for (String dat : getModels(
					new File(System.getProperty("user.home"), ".opensha/"+erfName+"/"+erfName+".json"))) {
				File model = new File(System.getProperty("user.home"), ".opensha/"+erfName+"/" + dat + ".zip");
				System.out.println("Checking if " + model.toString() + " exists...");
				if (!model.exists()) {
					System.out.println(model.toString() + " is missing!");
					return false;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return true;
	}
		
	public static void main(String[] args) {
        boolean showProgress = false;
        Map<String, GetFile> downloaders = Map.of(
                "ucerf3", new UCERF3_Downloader(showProgress),
                "nshm23", new NSHM23_Downloader(showProgress)
        );
        for (var entry : downloaders.entrySet()) {
            updateERF(entry.getKey(), entry.getValue());
        }
	}
}

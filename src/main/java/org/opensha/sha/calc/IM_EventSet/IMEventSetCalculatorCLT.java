package org.opensha.sha.calc.IM_EventSet;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.*;
import java.util.logging.Level;

import org.apache.commons.cli.*;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.data.siteData.impl.WillsMap2000;
import org.opensha.commons.data.siteData.impl.WillsMap2006;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.event.ParameterChangeWarningEvent;
import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01Writer;
import org.opensha.sha.calc.IM_EventSet.outputImpl.OriginalModWriter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.earthquake.param.AleatoryMagAreaStdDevParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.HistoricOpenIntervalParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel02.Frankel02_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF1.WGCEP_UCERF1_EqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenuationRelationship;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.USGS_Combined_2004_AttenRel;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.mean.MeanUCERF3;
import scratch.UCERF3.erf.mean.MeanUCERF3.Presets;



/**
 * <p>Title: IMEventSetCalculatorCLT</p>
 *
 * <p>Description: This class computes the Mean and Sigma for any Attenuation
 * supported and any IMT supported by these AttenuationRelationships.
 * Sites information is read from an input file.
 * </p>
 *
 * @author Ned Field, Nitin Gupta and Vipin Gupta
 * @version 1.0
 */
public class IMEventSetCalculatorCLT extends AbstractIMEventSetCalc
implements ParameterChangeWarningListener {

	protected LocationList locList;

    // Selected ERF
	protected ERF forecast;

	// Supported Attenuations
	protected ArrayList<ScalarIMR> chosenAttenuationsList;

	// Static IMT names
	protected ArrayList<String> supportedIMTs;

	protected String dirName = "MeanSigma"; // default output dir name
	private File outputDir;
	

	private ArrayList<ArrayList<SiteDataValue<?>>> userDataVals;

    // All supported ERFs - call .instance() to get the BaseERF
    private static final Set<ERF_Ref> erfRefs = IMEventSetERFUtils.getSupportedERFs();
    // Map of ERF names to their references for quick lookup
    private static final Map<String, ERF_Ref> erfNameMap = new HashMap<>();
    private static final ArrayList<String> erfShortNames = new ArrayList<>();
    private static final ArrayList<String> erfLongNames = new ArrayList<>();

    /**
	 *  ArrayList that maps picklist attenRel string names to the real fully qualified
	 *  class names
	 */
	private static final ArrayList<String> attenRelClasses = new ArrayList<>();
	private static final ArrayList<String> imNames = new ArrayList<>();
    private static final OrderedSiteDataProviderList providers;

	static {
        // Initialize ERF name map
        for (ERF_Ref ref : erfRefs) {
            // Allow users to reference ERF by long or short names
            String erfName = ref.toString();
            String erfShortName = ref.getERFClass().getSimpleName();
            erfNameMap.put(erfName, ref);
            erfNameMap.put(erfShortName, ref);
            erfShortNames.add(erfShortName);
            erfLongNames.add(erfName);
        }

//		imNames.add(CY_2006_AttenRel.NAME);
//		attenRelClasses.add(CY_2006_AttenRel.class.getName());
//		imNames.add(CY_2008_AttenRel.NAME);
//		attenRelClasses.add(CY_2008_AttenRel.class.getName());
//		imNames.add(CB_2006_AttenRel.NAME);
//		attenRelClasses.add(CB_2006_AttenRel.class.getName());
//		imNames.add(CB_2008_AttenRel.NAME);
//		attenRelClasses.add(CB_2008_AttenRel.class.getName());
//		imNames.add(BA_2006_AttenRel.NAME);
//		attenRelClasses.add(BA_2006_AttenRel.class.getName());
//		imNames.add(BA_2008_AttenRel.NAME);
//		attenRelClasses.add(BA_2008_AttenRel.class.getName());
//		imNames.add(CS_2005_AttenRel.NAME);
//		attenRelClasses.add(CS_2005_AttenRel.class.getName());
//		imNames.add(BJF_1997_AttenRel.NAME);
//		attenRelClasses.add(BJF_1997_AttenRel.class.getName());
//		imNames.add(AS_1997_AttenRel.NAME);
//		attenRelClasses.add(AS_1997_AttenRel.class.getName());
//		imNames.add(AS_2008_AttenRel.NAME);
//		attenRelClasses.add(AS_2008_AttenRel.class.getName());
//		imNames.add(Campbell_1997_AttenRel.NAME);
//		attenRelClasses.add(Campbell_1997_AttenRel.class.getName());
//		imNames.add(SadighEtAl_1997_AttenRel.NAME);
//		attenRelClasses.add(SadighEtAl_1997_AttenRel.class.getName());
//		imNames.add(Field_2000_AttenRel.NAME);
//		attenRelClasses.add(Field_2000_AttenRel.class.getName());
//		imNames.add(Abrahamson_2000_AttenRel.NAME);
//		attenRelClasses.add(Abrahamson_2000_AttenRel.class.getName());
//		imNames.add(CB_2003_AttenRel.NAME);
//		attenRelClasses.add(CB_2003_AttenRel.class.getName());
//		imNames.add(BS_2003_AttenRel.NAME);
//		attenRelClasses.add(BS_2003_AttenRel.class.getName());
//		imNames.add(BC_2004_AttenRel.NAME);
//		attenRelClasses.add(BC_2004_AttenRel.class.getName());
//		imNames.add(GouletEtAl_2006_AttenRel.NAME);
//		attenRelClasses.add(GouletEtAl_2006_AttenRel.class.getName());
//		imNames.add(ShakeMap_2003_AttenRel.NAME);
//		attenRelClasses.add(ShakeMap_2003_AttenRel.class.getName());
//		imNames.add(SEA_1999_AttenRel.NAME);
//		attenRelClasses.add(SEA_1999_AttenRel.class.getName());

		for (AttenRelRef ref : AttenRelRef.get(ServerPrefUtils.SERVER_PREFS)) {
			try {
				String name = ref.getName();
				String className = ref.getAttenRelClass().getName();
				imNames.add(name);
				attenRelClasses.add(className);
			} catch (Exception e) {
				// skip that IMR
			}
		}
        ArrayList<SiteData<?>> p = new ArrayList<>();
        try {
            p.add(new WillsMap2006());
        } catch (IOException e) {
            throw ExceptionUtils.asRuntimeException(e);
        }
        providers = new OrderedSiteDataProviderList(p);
        // disable non-Vs30 providers
        for (int i=0; i<providers.size(); i++) {
            if (!providers.getProvider(i).getDataType().equals(SiteData.TYPE_VS30))
                providers.setEnabled(i, false);
        }
	}

    /**
     * Legacy constructor for parsing legacy input file.
     * @param inpFile
     * @param outDir
     */
	public IMEventSetCalculatorCLT(String inpFile, String outDir) {
        String inputFileName = "MeanSigmaCalc_InputFile.txt"; // Default
        if (!(inpFile == null || inpFile.isEmpty())) {
            inputFileName = inpFile;
        }
        if (!(outDir == null || outDir.isEmpty())) {
            dirName = outDir;
            outputDir = new File(dirName);
        }
        try {
            parseLegacyInputFile(inputFileName);
        } catch (Exception ex) {
            logger.log(Level.INFO, "Error parsing input file!", ex);
            System.exit(1);
        }
	}

    /**
     * New input constructor with improved flexibility
     * @param erfName           String for short or long name of the ERF
     * @param bgSeismicity
     * @param rupOffset
     * @param attenRelNames     All IMR names as list of strings
     * @param imtNames          All IMT names as list of strings
     * @param siteFile
     * @param outDir
     */
    public IMEventSetCalculatorCLT(String erfName,
                                   String bgSeismicity,
                                   double rupOffset,
                                   ArrayList<String> attenRelNames,
                                   ArrayList<String> imtNames,
                                   String siteFile,
                                   String outDir) {
        getERF(erfName);
        toApplyBackGroud(bgSeismicity);
        setRupOffset(rupOffset);
        for (String attenRel : attenRelNames) {
            setIMR(attenRel);
        }
        for (String imt : imtNames) {
            setIMT(imt);
        }
        if (siteFile == null || siteFile.isEmpty()) {
            // If no sites file provided, default to 1 site in LA with Vs30 760m/s
            setSite("34.1 -118.1 760");
        } else {
            try {
                parseSitesInputCSV(siteFile);
            } catch (Exception ex) {
                logger.log(Level.INFO, "Error parsing input file!", ex);
                System.exit(1);
            }
        }
        if (!(outDir == null || outDir.isEmpty())) {
            dirName = outDir;
            outputDir = new File(dirName);
        }
    }

    /**
     * For new input format, sites are collected directly from a CSV file
     * @param siteFile
     */
    private void parseSitesInputCSV(String siteFile) throws IOException {
        ArrayList<String> fileLines;

        logger.log(Level.INFO, "Parsing sites input file: " + siteFile);

        fileLines = FileUtils.loadFile(siteFile);
        if (fileLines.isEmpty()) {
            throw new RuntimeException("Input file empty or doesn't exist! " + siteFile);
        }
        for (String fileLine : fileLines) {
            String line = fileLine.trim();
            // if it is comment skip to next line
            if (line.startsWith("#") || line.isEmpty()) continue;
            setSite(line, ",");
        }
    }

    /**
     * Legacy input format parses every input with one large TXT file
     * @param inputFileName
     * @throws IOException
     */
	private void parseLegacyInputFile(String inputFileName) throws IOException {
		ArrayList<String> fileLines;
		
		logger.log(Level.INFO, "Parsing input file: " + inputFileName);

		fileLines = FileUtils.loadFile(inputFileName);
		
		if (fileLines.isEmpty()) {
			throw new RuntimeException("Input file empty or doesn't exist! " + inputFileName);
		}

		int j = 0;
		int numIMRdone=0;
		int numIMRs=0;
		int numIMTdone=0;
		int numIMTs=0;
		int numSitesDone= 0;
		int numSites =0;
        for (String fileLine : fileLines) {
            String line = fileLine.trim();
            // if it is comment skip to next line
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (j == 0) getERF(line);
            if (j == 1) {
                toApplyBackGroud(line.trim());
            }
            if (j == 2) {
                double rupOffset = Double.parseDouble(line.trim());
                setRupOffset(rupOffset);
            }
            if (j == 3)
                numIMRs = Integer.parseInt(line.trim());
            if (j == 4) {
                setIMR(line.trim());
                ++numIMRdone;
                if (numIMRdone == numIMRs)
                    ++j;
                continue;
            }
            if (j == 5)
                numIMTs = Integer.parseInt(line.trim());
            if (j == 6) {
                setIMT(line.trim());
                ++numIMTdone;
                if (numIMTdone == numIMTs)
                    ++j;
                continue;
            }
            if (j == 7)
                numSites = Integer.parseInt(line.trim());
            if (j == 8) {
                setSite(line.trim());
                ++numSitesDone;
                if (numSitesDone == numSites)
                    ++j;
                continue;
            }
            ++j;
        }
	}

    private void setSite(String line) {
        // Default delimiter: any whitespace
        setSite(line, "\\s+");
    }

	/**
	 * Gets the list of locations with their Wills Site Class or Vs30 values
	 * @param line String
     * @param delim Delimiter
	 */
    private void setSite(String line, String delim) {
        if (locList == null)
            locList = new LocationList();
        if (userDataVals == null)
            userDataVals = new ArrayList<ArrayList<SiteDataValue<?>>>();

        // Split by the specified delimiter
        String[] tokens = line.trim().split(delim);
        int tokenCount = tokens.length;

        if(tokenCount > 3 || tokenCount < 2) {
            throw new RuntimeException("Must Enter valid Lat Lon in each line in the file");
        }

        double lat = Double.parseDouble(tokens[0].trim());
        double lon = Double.parseDouble(tokens[1].trim());
        Location loc = new Location(lat,lon);
        locList.add(loc);
        ArrayList<SiteDataValue<?>> dataVals = new ArrayList<SiteDataValue<?>>();
        String dataVal = null;
        if (tokenCount == 3) {
            dataVal = tokens[2].trim();
        }
        if (WillsMap2000.wills_vs30_map.containsKey(dataVal)) {
            // this is a Wills Class
            dataVals.add(new SiteDataValue<String>(SiteData.TYPE_WILLS_CLASS,
                    SiteData.TYPE_FLAG_MEASURED, dataVal));
        } else if (dataVal != null) {
            // Vs30 value
            try {
                double vs30 = Double.parseDouble(dataVal);
                dataVals.add(new SiteDataValue<Double>(SiteData.TYPE_VS30,
                        SiteData.TYPE_FLAG_MEASURED, vs30));
            } catch (NumberFormatException e) {
                System.err.println("*** WARNING: Site Wills/Vs30 value unknown: " + dataVal);
            }
        }
        userDataVals.add(dataVals);
    }

	/**
	 * Sets the supported IMTs as String
	 * @param line String
	 */
	private void setIMT(String line) {
		if(supportedIMTs == null)
			supportedIMTs = new ArrayList<String>();
		this.supportedIMTs.add(line.trim());
	}

	/**
	 * Creates the IMR instances and adds to the list of supported IMRs
	 * @param str String
	 */
	private void setIMR(String str) {
		if(chosenAttenuationsList == null)
			chosenAttenuationsList = new ArrayList<ScalarIMR>();
		String imrName = str.trim();
		int index = imNames.indexOf(imrName);
		createIMRClassInstance(attenRelClasses.get(index));
	}

	/**
	 * Creates a class instance from a string of the full class name including packages.
	 * This is how you dynamically make objects at runtime if you don't know which\
	 * class beforehand. For example, if you wanted to create a BJF_1997_AttenRel you can do
	 * it the normal way:<P>
	 *
	 * <code>BJF_1997_AttenRel imr = new BJF_1997_AttenRel()</code><p>
	 *
	 * If your not sure the user wants this one or AS_1997_AttenRel you can use this function
	 * instead to create the same class by:<P>
	 *
	 * <code>BJF_1997_AttenRel imr =
	 * (BJF_1997_AttenRel)ClassUtils.createNoArgConstructorClassInstance("org.opensha.sha.imt.attenRelImpl.BJF_1997_AttenRel");
	 * </code><p>
	 *
	 */
	protected void createIMRClassInstance(String AttenRelClassName) {
		try {
			Class listenerClass = ParameterChangeWarningListener.class;
			Object[] paramObjects = new Object[] {
					this};
			Class[] params = new Class[] {
					listenerClass};
			Class imrClass = Class.forName(AttenRelClassName);
			Constructor con = imrClass.getConstructor(params);
			AttenuationRelationship attenRel = (AttenuationRelationship) con.newInstance(paramObjects);
			if(attenRel.getName().equals(USGS_Combined_2004_AttenRel.NAME))
				throw new RuntimeException("Cannot use "+USGS_Combined_2004_AttenRel.NAME+" in calculation of Mean and Sigma");
			//setting the Attenuation with the default parameters
			attenRel.setParamDefaults();
			chosenAttenuationsList.add(attenRel);
		}
		catch (ClassCastException e) {
			e.printStackTrace();
		}
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		catch (InstantiationException e) {
			e.printStackTrace();
		}
	}

    /**
     * Creates an ERF instance from the string parsed as an erfName.
     * ERFs are created with default parameters, with a few hardcoded exceptions.
     * All values set in getERF can be overriden by custom parameters in `parseLegacyInputFile`.
     * @param line user input to parse into an erfName
     */
	private void getERF(String line){
		String erfName = line.trim();
		logger.log(Level.CONFIG, "Attempting to identify ERF from name: " + erfName);

        ERF_Ref erfRef = erfNameMap.get(erfName);
        // For backwards compatibility, these ERFs are hardcoded with special default parameters.
		if (erfName.equals(Frankel02_AdjustableEqkRupForecast.NAME)
                || erfName.equals(Frankel02_AdjustableEqkRupForecast.class.getSimpleName())) {
            createFrankel02Forecast();
        } else if (erfName.equals(WGCEP_UCERF1_EqkRupForecast.NAME)
                || erfName.equals(WGCEP_UCERF1_EqkRupForecast.class.getSimpleName())) {
            createUCERF1_Forecast();
        } else if (erfName.equals(MeanUCERF2.NAME)
                || erfName.equals(MeanUCERF2.class.getSimpleName())) {
            createMeanUCERF2_Forecast();
        } else if (erfName.startsWith("Mean UCERF3") || erfName.startsWith("UCERF3")) {
            createMeanUCERF3_Forecast(erfName);
        } else if (erfRef != null) {
            // Non-hardcoded ERFs are created with default parameters.
            logger.log(Level.CONFIG, "Creating ERF dynamically with default parameters.");
            forecast = (ERF)erfRef.instance();
        } else {
            throw new RuntimeException ("Unsupported ERF");
        }
        // Only set duration to 1.0 if the ERF allows it
        if (!(forecast instanceof MeanUCERF3)) {
            try {
                // Check if the duration parameter allows 1.0
                forecast.getTimeSpan().setDuration(1.0);
                logger.log(Level.FINE, "Set duration to 1.0 for ERF: " + erfName);
            } catch (ConstraintException e) {
                // ERF has constraints that don't allow duration=1.0, use default duration
                logger.log(Level.WARNING, "ERF " + erfName + " does not allow duration=1.0, " +
                        "using default duration: " + forecast.getTimeSpan().getDuration());
            }
        }
	}

	/**
	 * Creating the instance of the Frankel02 forecast
	 */
	private void createFrankel02Forecast(){
		logger.log(Level.CONFIG, "Creating Frankel02 ERF");
		forecast = new Frankel02_AdjustableEqkRupForecast();
	}

	/**
	 * Creating the instance of the UCERF1 Forecast
	 */
	private void createUCERF1_Forecast(){
		logger.log(Level.CONFIG, "Creating UCERF1 ERF");
		forecast = new WGCEP_UCERF1_EqkRupForecast();
		forecast.getAdjustableParameterList().getParameter(
				WGCEP_UCERF1_EqkRupForecast.TIME_DEPENDENT_PARAM_NAME).setValue(Boolean.valueOf(false));
	}

	/**
	 * Creating the instance of the UCERF2 - Single Branch Forecast
	 */
	private void createMeanUCERF2_Forecast(){
		logger.log(Level.CONFIG, "Creating UCERF2 ERF");
		forecast = new MeanUCERF2();
		forecast.getAdjustableParameterList().getParameter(
				UCERF2.PROB_MODEL_PARAM_NAME).setValue(UCERF2.PROB_MODEL_POISSON);
	}
	
	private void createMeanUCERF3_Forecast(String name) {
		name = name.trim();
		logger.log(Level.CONFIG, "Creating MeanUCERF3 ERF");
		MeanUCERF3.show_progress = false;
		MeanUCERF3 forecast = new MeanUCERF3();
		Presets preset;
		String args;
		if (name.startsWith("Mean UCERF3 FM3.1")) {
			preset = MeanUCERF3.Presets.FM3_1_BRANCH_AVG;
			args = name.substring("Mean UCERF3 FM3.1".length());
		} else if (name.startsWith("Mean UCERF3 FM3.2")) {
			preset = MeanUCERF3.Presets.FM3_2_BRANCH_AVG;
			args = name.substring("Mean UCERF3 FM3.2".length());
		} else {
			preset = MeanUCERF3.Presets.BOTH_FM_BRANCH_AVG;
			Preconditions.checkState(name.length() == "Mean UCERF3".length(),
					"Can't specify UCERF3-TD params for full model, must use individual Fault Model");
			args = "";
		}
		
		logger.log(Level.CONFIG, "MeanUCERF3 Preset: "+preset.name());
		
		forecast.setPreset(preset);
		
		if (!args.isEmpty()) {
			logger.log(Level.CONFIG, "Time dependent args: "+args);
			// time dependent
			args = args.trim().replaceAll("\t", " ");
			while (args.contains("  "))
				args = args.replaceAll("  ", " ");
			String[] split = args.split(" ");
			Preconditions.checkState(split.length == 1 || split.length == 2,
					"UCERF3-TD arguments: <start-year> [<duration>]");
			int startYear = Integer.parseInt(split[0]);
			double duration = 1d;
			if (split.length == 2)
				duration = Double.parseDouble(split[1]);
			
			logger.log(Level.CONFIG, "Start Year: "+startYear);
			logger.log(Level.CONFIG, "Duration: "+duration);
			
//			erf.getParameter(IncludeBackgroundParam.NAME).setValue(IncludeBackgroundOption.INCLUDE);
//			erf.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, false);
			forecast.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.U3_PREF_BLEND);
			forecast.setParameter(AleatoryMagAreaStdDevParam.NAME, 0.0);
			forecast.setParameter(HistoricOpenIntervalParam.NAME, startYear-1875d);
			forecast.getTimeSpan().setStartTime(startYear);
			forecast.getTimeSpan().setDuration(duration);
		} else {
			forecast.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			forecast.getTimeSpan().setDuration(1d);
		}
		
		this.forecast = forecast;
	}

	private void toApplyBackGroud(String toApply){
		try {
			Parameter param = forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.BACK_SEIS_NAME);
			logger.log(Level.FINE, "Setting ERF background seismicity value: " + toApply);
			if (param instanceof StringParameter) {
				param.setValue(toApply);
			} else if (param instanceof IncludeBackgroundParam) {
				IncludeBackgroundOption val = IncludeBackgroundOption.valueOf(toApply.trim().toUpperCase());
				param.setValue(val);
			}
		} catch (ParameterException e) {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_NAME+"', ignoring setting.");
		}
		if(!(forecast instanceof MeanUCERF2)) {
			if (forecast.getAdjustableParameterList().containsParameter(
					Frankel02_AdjustableEqkRupForecast.
					BACK_SEIS_RUP_NAME)) { 	
				Parameter param = forecast.getAdjustableParameterList().getParameter(
						Frankel02_AdjustableEqkRupForecast.BACK_SEIS_RUP_NAME);
				if (param instanceof StringParameter)
					param.setValue(Frankel02_AdjustableEqkRupForecast.
								BACK_SEIS_RUP_FINITE);
				else if (param instanceof BackgroundRupParam)
					param.setValue(BackgroundRupType.FINITE);
			}
		}

	}

	private void setRupOffset(double rupOffset){
		if (forecast.getAdjustableParameterList().containsParameter(Frankel02_AdjustableEqkRupForecast.
				RUP_OFFSET_PARAM_NAME)) {
			logger.log(Level.FINE, "Setting ERF rupture offset: " + rupOffset);
			forecast.getAdjustableParameterList().getParameter(
					Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME).setValue(Double.valueOf(rupOffset));
		} else {
			logger.log(Level.WARNING, "ERF doesn't contain param '"+Frankel02_AdjustableEqkRupForecast.
					RUP_OFFSET_PARAM_NAME+"', ignoring setting.");
		}
		forecast.updateForecast();
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma() throws IOException {
		getMeanSigma(false);
	}
	
	/**
	 * Starting with the Mean and Sigma calculation.
	 * Creates the directory to put the mean and sigma files.
	 * @throws IOException 
	 */
	public void getMeanSigma(boolean haz01) throws IOException {

		int numIMRs = chosenAttenuationsList.size();
		File file = new File(dirName);
		file.mkdirs();
		IM_EventSetOutputWriter writer = null;
		if (haz01) {
			writer = new HAZ01Writer(this);
		} else {
			writer = new OriginalModWriter(this);
		}
		writer.writeFiles(forecast, chosenAttenuationsList, supportedIMTs);
	}

	/**
	 *  Function that must be implemented by all Listeners for
	 *  ParameterChangeWarnEvents.
	 *
	 * @param e The Event which triggered this function call
	 */
	public void parameterChangeWarning(ParameterChangeWarningEvent e) {

		String S = " : parameterChangeWarning(): ";

		WarningParameter param = e.getWarningParameter();

		param.setValueIgnoreWarning(e.getNewValue());

	}

    private static void listERFs() {
        System.out.println("Available Earthquake Rupture Forecasts (ERFs):");
        System.out.println("==============================================");
        System.out.println();

        // Find the maximum length of short names for proper alignment
        int maxShortNameLength = 0;
        for (String shortName : erfShortNames) {
            if (shortName.length() > maxShortNameLength) {
                maxShortNameLength = shortName.length();
            }
        }
        // Ensure minimum width for alignment
        maxShortNameLength = Math.max(maxShortNameLength, 10);

        for (int i = 0; i < erfShortNames.size(); i++) {
            String shortName = erfShortNames.get(i);
            String longName = erfLongNames.get(i);
            // Format the output with fixed width for short name
            System.out.printf("%-" + maxShortNameLength + "s – %s%n", shortName, longName);
        }

        System.out.println();
        System.out.println("Usage: imcalc -e \"FULL_NAME\" ... or imcalc -e ShortName ...");
        System.out.println("Example: imcalc -e \"STEP Alaskan Pipeline ERF\" ...");
        System.out.println("Example: imcalc -e STEP_AlaskanPipeForecast ...");
    }

    /**
     * How to use the CLT with the new interface. Use `--help` to see this.
     * @param options
     */
    private static void printUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null); // Preserve declaration order

        String header = "\nIM Event Set Calculator - Compute Mean and Sigma for Attenuation Relationships and IMTs\n\n";

        String footer = "\nExample:\n" +
                "  imcalc -e \"WGCEP (2007) UCERF2 - Single Branch\" \\\n" +
                "         -b Exclude \\\n" +
                "         -a \"Boore & Atkinson (2008)\",\"Chiou & Youngs (2008)\" \\\n" +
                "         -m \"PGA,SA200,SA 1.0\" \\\n" +
                "         -r 5 \\\n" +
                "         -s sites.csv \\\n" +
                "         -o results/\n\n" +
                "Legacy-mode usage:\n" +
                "  imcalc --legacy <legacy-input.txt> <output-dir>\n\n" +
                "Note: Site data value (Vs30 or Wills Class) is optional in CSV file.\n" +
                "      Use IMEventSetCalculatorGUI for more refined control of Site Parameters.\n" +
                "      Legacy input file format is deprecated. Consider migrating to new-style options.\n";

        formatter.printHelp("imcalc [OPTIONS] [--] [<legacy-file> <output-dir>]",
                header, options, footer, true);
    }

    /**
     * How to use the CLT with the legacy interface. Use `--legacy --help` to see this.
     * @param options
     */
    private static void printLegacyUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null); // Preserve declaration order

        String header = "\nIM Event Set Calculator - Legacy Mode\n\n" +
                "Uses fixed-format input file. See example input file for format details.\n\n";

        String footer = "\nLegacy-mode usage:\n" +
                "  imcalc --legacy <legacy-input.txt> <output-dir>\n\n" +
                "Example:\n" +
                "  imcalc --legacy ExampleLegacyInputFileCLT.txt output/\n" +
                "  imcalc --legacy --haz01 ExampleLegacyInputFileCLT.txt output/\n" +
                "  imcalc --legacy -d ExampleLegacyInputFileCLT.txt output/\n\n" +
                "Note: Legacy input file format is deprecated.\n" +
                "      Consider migrating to new command-line options for better flexibility.\n" +
                "      Use 'imcalc --help' (without --legacy) to see new-style options.\n";

        formatter.printHelp("imcalc --legacy [OPTIONS] <legacy-input.txt> <output-dir>",
                header, options, footer, true);
    }

    private static Level getLogLevel(CommandLine cmd) {
        if (cmd.hasOption("ddd")) return Level.ALL;
        if (cmd.hasOption("dd")) return Level.FINE;
        if (cmd.hasOption("d")) return Level.CONFIG;
        if (cmd.hasOption("q")) return Level.OFF;
        return Level.WARNING; // default
    }

    /**
     * Creates all options that can be interpreted by either the new input format
     * or the legacy input format.
     * @return
     */
    private static Options createFullOptions() {
        Options options = new Options();

        // Common options (available in both legacy and new-style)
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show this help and exit. Use `--legacy --help` for legacy-style help")
                .build());

        options.addOption(Option.builder()
                .longOpt("list-erfs")
                .desc("List available ERF short names and long names")
                .build());

        options.addOption(Option.builder()
                .longOpt("haz01")
                .desc("Use HAZ01 output file format instead of default")
                .build());

        options.addOption(Option.builder("d")
                .desc("Set logging level to CONFIG (verbose)")
                .build());

        options.addOption(Option.builder("dd")
                .desc("Set logging level to FINE (very verbose)")
                .build());

        options.addOption(Option.builder("ddd")
                .desc("Set logging level to ALL (debug)")
                .build());

        options.addOption(Option.builder("q")
                .longOpt("quiet")
                .desc("Set logging level to OFF (quiet)")
                .build());

        // New-style only options
        options.addOption(Option.builder("e")
                .longOpt("erf")
                .hasArg()
                .argName("name")
                .desc("Earthquake Rupture Forecast (ERF) - short code or full name in quotes")
                .build());

        options.addOption(Option.builder("b")
                .longOpt("background-seismicity")
                .hasArg()
                .argName("type")
                .desc("Include | Exclude | Only-Background")
                .build());

        options.addOption(Option.builder("r")
                .longOpt("rupture-offset")
                .hasArg()
                .argName("km")
                .desc("Rupture offset for floating ruptures (1-100 km; 5 km is generally best). Not applicable to UCERF3, but a value is still required.")
                .build());

        OptionGroup attenRelGroup = new OptionGroup(); // Mutually exclusive options
        attenRelGroup.addOption(Option.builder("a")
                .longOpt("atten-rels")
                .hasArg()
                .argName("IMR1,IMR2,...")
                .desc("Comma-separated in quotation attenuation relations")
                .build());
        attenRelGroup.addOption(Option.builder("f")
                .longOpt("atten-rels-file")
                .hasArg()
                .argName("file")
                .desc("Newlines-separated IMR list (mutually exclusive with --atten-rels)")
                .build());
        options.addOptionGroup(attenRelGroup);

        options.addOption(Option.builder("m")
                .longOpt("imts")
                .hasArg()
                .argName("IMT1,IMT2,...")
                .desc("Comma-separated intensity-measure types")
                .build());

        options.addOption(Option.builder("s")
                .longOpt("sites")
                .hasArg()
                .argName("csv-file")
                .desc("CSV of Lat, Lon, [Vs30/Wills] (column optional)")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output-dir")
                .hasArg()
                .argName("dir")
                .desc("Where to write results (defaults to current dir)")
                .build());

        options.addOption(Option.builder()
                .longOpt("legacy")
                .desc("Switch to legacy-file mode")
                .build());

        return options;
    }

    /**
     * The old input format was positional arguments for one TXT file with all
     * inputs and an output dir. These options are optional.
     * Dedicated function for legacyOptions is needed to generate usage.
     * @return
     */
    private static Options createLegacyOptions() {
        Options options = new Options();

        // Legacy mode only supports common options
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show legacy help and exit. Don't pass `--legacy` for new-style help.")
                .build());

        options.addOption(Option.builder()
                .longOpt("haz01")
                .desc("Use HAZ01 output file format instead of default")
                .build());

        options.addOption(Option.builder("d")
                .desc("Set logging level to CONFIG (verbose)")
                .build());

        options.addOption(Option.builder("dd")
                .desc("Set logging level to FINE (very verbose)")
                .build());

        options.addOption(Option.builder("ddd")
                .desc("Set logging level to ALL (debug)")
                .build());

        options.addOption(Option.builder("q")
                .longOpt("quiet")
                .desc("Set logging level to OFF (quiet)")
                .build());

        return options;
    }

    /**
     * Process legacy input parameters that are not common to new interface.
     * @param cmd optional arguments
     * @param remainingArgs Positional arguments
     * @return CLT instance constructed with legacy input params to use for calculation
     */
    private static IMEventSetCalculatorCLT processLegacyInput(
            CommandLine cmd,
            String[] remainingArgs) {
        Options options = createLegacyOptions();
        // Handle help option
        if (cmd.hasOption("help")) {
            printLegacyUsage(options);
            System.exit(0);
        }

        logger.log(Level.WARNING, "Using deprecated legacy input file format. Consider migrating to new command-line options.");
        logger.log(Level.WARNING, "See 'imcalc --help' for new-style usage.");

        // Validate that new-style options are not used in legacy mode
        String[] invalidOptions = {"erf", "background-seismicity", "atten-rels", "atten-rels-file", "imts", "sites", "output-dir"};
        for (String option : invalidOptions) {
            if (cmd.hasOption(option)) {
                System.err.println("Error: Option --" + option + " is not supported in legacy mode");
                System.err.println("Use a legacy input file or switch to new-style mode");
                System.exit(2);
            }
        }

        // Get the non-option arguments (positional arguments)
        if (remainingArgs.length != 2) {
            System.err.println("Error: Input file and output directory are required");
            System.err.println("Expected 2 positional arguments, found: " + remainingArgs.length);
            System.exit(2);
        }
        String inputFileName = remainingArgs[0];
        String outputDirName = remainingArgs[1];

        return new IMEventSetCalculatorCLT(inputFileName, outputDirName);
    }

    /**
     * Process new style input parameters that are not common to legacy interface.
     * @param cmd All arguments for parsing in the new input format
     * @return CLT instance constructed with legacy input params to use for calculation
     */
    private static IMEventSetCalculatorCLT processNewStyleInput(CommandLine cmd) {
        Options options = createFullOptions();
        // Handle help option
        if (cmd.hasOption("help")) {
            printUsage(options);
            System.exit(0);
        }
        // Show ERF options
        if (cmd.hasOption("list-erfs")) {
            listERFs();
            System.exit(0);
        }

        // Check required OptionGroup for attenuation relationships
        boolean hasAttenRels = cmd.hasOption("atten-rels");
        boolean hasAttenRelsFile = cmd.hasOption("atten-rels-file");
        if (!(hasAttenRels || hasAttenRelsFile)) {
            System.err.println("Error: At least one of --atten-rels or --atten-rels-file must be specified");
            printUsage(options);
            System.exit(2);
        }
        // Check for required options without mutual exclusivity
        String[] requiredOptions = {"erf", "background-seismicity", "rupture-offset", "imts", "output-dir"};
        for (String option : requiredOptions) {
            if (!cmd.hasOption(option)) {
                System.err.println("Error: Required option --" + option + " is missing");
                printUsage(options);
                System.exit(2);
            }
        }

        String erfName = cmd.getOptionValue("erf");
        // bgSeis value is ignored if ERF doesn't support it
        String bgSeis = cmd.getOptionValue("background-seismicity");
        double rupOffset = Double.parseDouble(cmd.getOptionValue("rupture-offset"));
        ArrayList<String> attenRels;
        if (hasAttenRels) {
            attenRels = parseQuotedString(cmd.getOptionValue("atten-rels"));
            System.out.println(attenRels);
        } else {
            try {
                attenRels = FileUtils.loadFile(cmd.getOptionValue("atten-rels-file"));
                // Filter comments out of attenRels input TXT file
                attenRels.removeIf(s -> s.startsWith("#"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        ArrayList<String> imts = new ArrayList<>(List.of(cmd.getOptionValue("imts").split(",")));

        String sites = null;
        if (cmd.hasOption("sites")) {
            sites = cmd.getOptionValue("sites");
        }

        String outputDirName = cmd.getOptionValue("output-dir");

        return new IMEventSetCalculatorCLT(erfName, bgSeis, rupOffset,
                attenRels, imts, sites, outputDirName);
    }

    /**
     * A string of values where each value in quotes is separated into a list
     * @param csvString     "first","second, with a comma","third"
     * @return              ["first", "second, with a comma", "third"]
     */
    private static ArrayList<String> parseQuotedString(String csvString) {
        ArrayList<String> values = new ArrayList<>();
        if (csvString == null || csvString.trim().isEmpty()) {
            return values;
        }

        // Split by comma, but be careful with quoted strings
        // This regex splits on commas that are NOT inside quotes
        String[] parts = csvString.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("\"") && part.endsWith("\"")) {
                // Remove surrounding quotes
                values.add(part.substring(1, part.length() - 1));
            } else {
                values.add(part);
            }
        }
        return values;
    }


    /**
     * Entry point to CLT. Parses input to determine input mode (new-style vs legacy),
     * directly processes common parameters, and calls the appropriate processor.
     * Exits with 0 on success, 1 on failure, and 2 on invalid input.
     * @param args
     */
	public static void main(String[] args) {
        // First, parse with full options to detect mode
        Options options = createFullOptions();
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args, true); // Stop at non-option
        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getMessage());
            printUsage(options);
            System.exit(2);
        }

        // Determine mode
        boolean legacyMode = cmd.hasOption("legacy");
        String[] remainingArgs = cmd.getArgs();

        Level level = getLogLevel(cmd);
        initLogger(level);

        // If legacy mode not explicitly set but .txt file detected, use legacy mode
        // This effectively means that the --legacy flag is optional and existing users
        // of the legacy format won't be impacted by the new input format.
        if (!legacyMode && remainingArgs.length > 0 && remainingArgs[0].toLowerCase().endsWith(".txt")) {
            legacyMode = true;
        }

        // Output mode
        boolean haz01 = cmd.hasOption("haz01");

        // Process input according to legacy or new input mode and initialize calculator
        IMEventSetCalculatorCLT calc = null;
        if (legacyMode) {
            calc = processLegacyInput(cmd, remainingArgs);
        } else {
           calc = processNewStyleInput(cmd);
        }

        // Invoke calculator
        try {
            calc.getMeanSigma(haz01);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);

	}

	public int getNumSites() {
		return locList.size();
	}

	public File getOutputDir() {
		return outputDir;
	}

	public OrderedSiteDataProviderList getSiteDataProviders() {
		return providers;
	}

	public Location getSiteLocation(int i) {
		return locList.get(i);
	}

	public ArrayList<SiteDataValue<?>> getUserSiteDataValues(int i) {
		return userDataVals.get(i);
	}
}

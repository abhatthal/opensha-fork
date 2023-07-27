package org.opensha.sha.earthquake.faultSysSolution.erf;

import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.EXCLUDE;
import static org.opensha.sha.earthquake.param.IncludeBackgroundOption.ONLY;

import java.io.File;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.impl.FileParameter;
import org.opensha.sha.earthquake.AbstractNthRupERF;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

/**
 * Basic fault system solution ERF without any time dependence or model-specific features
 */
public class BaseFaultSystemSolutionERF extends AbstractNthRupERF {

	private static final long serialVersionUID = 1L;

	protected static final boolean D = false;
	
	public static final String NAME = "Fault System Solution ERF";
	private String name = NAME;
	
	// Adjustable parameters
	public static final String FILE_PARAM_NAME = "Solution Input File";
	protected FileParameter fileParam;
	protected boolean includeFileParam = true;
	protected FaultGridSpacingParam faultGridSpacingParam;
	protected IncludeBackgroundParam bgIncludeParam;
	protected BackgroundRupParam bgRupTypeParam;
	
	// The primitive versions of parameters; and values here are the param defaults: (none for fileParam)
	protected double faultGridSpacing = 1.0;
	protected IncludeBackgroundOption bgInclude = IncludeBackgroundOption.INCLUDE;
	protected BackgroundRupType bgRupType = BackgroundRupType.POINT;
	
	// Parameter change flags:
	protected boolean fileParamChanged=false;	// set as false since most subclasses ignore this parameter
	protected boolean faultGridSpacingChanged=true;
	protected boolean bgIncludeChanged=true;
	protected boolean bgRupTypeChanged=true;
	protected boolean quadSurfacesChanged=true;
	
	// TimeSpan stuff:
	protected final static double DURATION_DEFAULT = 30;	// years
	protected final static double DURATION_MIN = 0.0001;
	public final static double DURATION_MAX = 1000000;
	protected boolean timeSpanChangeFlag=true;	// this keeps track of time span changes
	
	// solution and constants
	protected FaultSystemSolution faultSysSolution;		// the FFS for the ERF
	protected RupMFDsModule mfdsModule;					// rupture MFDs
	protected boolean cacheGridSources = false;			// if true, grid sources are cached instead of built on the fly
	protected ProbEqkSource[] gridSourceCache = null;
	protected int numNonZeroFaultSystemSources;			// this is the number of faultSystemRups with non-zero rates (each is a source here)
	protected int totNumRupsFromFaultSystem;						// the sum of all nth ruptures that come from fault system sources (and not equal to faultSysSolution.getNumRuptures())

	protected int numOtherSources=0; 					// the non fault system sources
	protected int[] fltSysRupIndexForSource;  			// used to keep only inv rups with non-zero rates
	protected int[] srcIndexForFltSysRup;				// this stores the src index for the fault system source (-1 if there is no mapping)
	protected int[] fltSysRupIndexForNthRup;			// the fault system rupture index for the nth rup
	protected double[] longTermRateOfFltSysRupInERF;	// this holds the long-term rate of FSS rups as used by this ERF
	
	// these help keep track of what's changed
	protected boolean faultSysSolutionChanged = true;	
	
	protected List<FaultRuptureSource> faultSourceList;
	
	public BaseFaultSystemSolutionERF() {
		this(true);
	}
	
	protected BaseFaultSystemSolutionERF(boolean doInit) {
		if (doInit) {
			initParams();
			initTimeSpan(); // must be done after the above because this depends on probModelParam
		}
	}

	@Override
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		Preconditions.checkArgument(!StringUtils.isBlank(name), "Name cannot be empty");
		this.name = name;
	}
	
	protected void initParams() {
		fileParam = new FileParameter(FILE_PARAM_NAME);
		faultGridSpacingParam = new FaultGridSpacingParam();
		bgIncludeParam = new IncludeBackgroundParam();
		bgRupTypeParam = new BackgroundRupParam();


		// set listeners
		fileParam.addParameterChangeListener(this);
		faultGridSpacingParam.addParameterChangeListener(this);
		bgIncludeParam.addParameterChangeListener(this);
		bgRupTypeParam.addParameterChangeListener(this);

		
		// set parameters to the primitive values
		// don't do anything here for fileParam 
		faultGridSpacingParam.setValue(faultGridSpacing);
		bgIncludeParam.setValue(bgInclude);
		bgRupTypeParam.setValue(bgRupType);

		createParamList();
	}
	
	/**
	 * Put parameters in theParameterList
	 */
	protected void createParamList() {
		adjustableParams = new ParameterList();
		if(includeFileParam)
			adjustableParams.addParameter(fileParam);
		adjustableParams.addParameter(bgIncludeParam);
		if(!bgIncludeParam.getValue().equals(IncludeBackgroundOption.EXCLUDE)) {
			adjustableParams.addParameter(bgRupTypeParam);
		}
		adjustableParams.addParameter(faultGridSpacingParam);
	}
	
	/**
	 * This initiates the timeSpan.
	 */
	protected void initTimeSpan() {
		timeSpan = new TimeSpan(TimeSpan.NONE, TimeSpan.YEARS);
		timeSpan.setDuration(DURATION_DEFAULT);
		timeSpan.addParameterChangeListener(this);
	}
	
	/**
	 * This returns the number of fault system sources
	 * (that have non-zero rates)
	 * @return
	 */
	public int getNumFaultSystemSources(){
		return numNonZeroFaultSystemSources;
	}
	
	@Override
	public void parameterChange(ParameterChangeEvent event) {
		String paramName = event.getParameterName();
		if(paramName.equalsIgnoreCase(fileParam.getName())) {
			fileParamChanged=true;
		} else if(paramName.equalsIgnoreCase(faultGridSpacingParam.getName())) {
			faultGridSpacing = faultGridSpacingParam.getValue();
			faultGridSpacingChanged=true;
		} else if (paramName.equalsIgnoreCase(bgIncludeParam.getName())) {
			bgInclude = bgIncludeParam.getValue();
			createParamList();
			bgIncludeChanged = true;
			if (bgInclude != EXCLUDE && numOtherSources == 0)
				bgRupTypeChanged = true;
		} else if (paramName.equalsIgnoreCase(bgRupTypeParam.getName())) {
			bgRupType = bgRupTypeParam.getValue();
			bgRupTypeChanged = true;
		} else {
			throw new RuntimeException("parameter name not recognized");
		}
		
	}
	
	protected boolean shouldRebuildFaultSystemSources() {
		return faultSysSolutionChanged || faultGridSpacingChanged || quadSurfacesChanged || timeSpanChangeFlag;
	}
	
	/**
	 * Can be overridden to update data needed for building other (gridded) sources
	 */
	protected void updateHookBeforeOtherBuild() {
		// do nothing (can be overridden)
	}
	
	/**
	 * Can be overridden to update data needed for building fault system sources
	 */
	protected void updateHookBeforeFaultSourceBuild() {
		// do nothing (can be overridden)
	}
	
	@Override
	public void updateForecast() {
		
		if (D) System.out.println("Updating forecast");
		long runTime = System.currentTimeMillis();
		
		// read FSS solution from file if specified;
		// this sets faultSysSolutionChanged and bgRupTypeChanged (since this is obtained from the FSS) as true
		if(fileParamChanged) {
			readFaultSysSolutionFromFile();	// this will not re-read the file if the name has not changed
		}
		
		// update other sources if needed
		boolean numOtherRupsChanged=false;	// this is needed below
		if (bgIncludeChanged || bgRupTypeChanged || timeSpanChangeFlag) {
			updateHookBeforeOtherBuild();
			numOtherRupsChanged = initOtherSources();	// these are created even if not used; this sets numOtherSources
			gridSourceCache = null;
		}
		
		// update following FSS-related arrays if needed: longTermRateOfFltSysRupInERF[], srcIndexForFltSysRup[], fltSysRupIndexForSource[], numNonZeroFaultSystemSources
		boolean numFaultRupsChanged = false;	// needed below as well
		if (faultSysSolutionChanged) {	
			makeMiscFSS_Arrays(); 
			numFaultRupsChanged = true;	// not necessarily true, but a safe assumption
		}

		// now make the list of fault-system sources if any of the following have changed
		if (shouldRebuildFaultSystemSources()) {
			updateHookBeforeFaultSourceBuild();
			makeAllFaultSystemSources();	// overrides all fault-based source objects; created even if not fault sources aren't wanted
		}
		
		// update the following ERF rup-related fields: totNumRups, totNumRupsFromFaultSystem, nthRupIndicesForSource, srcIndexForNthRup[], rupIndexForNthRup[], fltSysRupIndexForNthRup[]
		if(numOtherRupsChanged || numFaultRupsChanged) {
			setAllNthRupRelatedArrays();
		}

		// reset change flags (that haven't already been done so)
		fileParamChanged = false;
		faultSysSolutionChanged = false;
		faultGridSpacingChanged = false;
		bgIncludeChanged = false;
		bgRupTypeChanged = false;			
		quadSurfacesChanged= false;
		timeSpanChangeFlag = false;
		
		runTime = (System.currentTimeMillis()-runTime)/1000;
		if(D) {
			System.out.println("Done updating forecast (took "+runTime+" seconds)");
			System.out.println("numFaultSystemSources="+numNonZeroFaultSystemSources);
			System.out.println("totNumRupsFromFaultSystem="+totNumRupsFromFaultSystem);
			System.out.println("totNumRups="+totNumRups);
			System.out.println("numOtherSources="+this.numOtherSources);
			System.out.println("getNumSources()="+this.getNumSources());
		}
		
	}
	
	protected boolean isRuptureIncluded(int fltSystRupIndex) {
		return true;
	}
	
	/**
	 * This method initializes the following arrays:
	 * 
	 *		longTermRateOfFltSysRupInERF[]
	 * 		srcIndexForFltSysRup[]
	 * 		fltSysRupIndexForSource[]
	 * 		numNonZeroFaultSystemSources
	 */
	private void makeMiscFSS_Arrays() {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		longTermRateOfFltSysRupInERF = new double[rupSet.getNumRuptures()];
		mfdsModule = faultSysSolution.getModule(RupMFDsModule.class);
				
		if(D) {
			System.out.println("Running makeFaultSystemSources() ...");
//			System.out.println("   aleatoryMagAreaStdDev = "+aleatoryMagAreaStdDev);
			System.out.println("   faultGridSpacing = "+faultGridSpacing);
			System.out.println("   faultSysSolution.getNumRuptures() = "
					+rupSet.getNumRuptures());
		}
		
		numNonZeroFaultSystemSources =0;
		ArrayList<Integer> fltSysRupIndexForSourceList = new ArrayList<Integer>();
		srcIndexForFltSysRup = new int[rupSet.getNumRuptures()];
		for(int i=0; i<srcIndexForFltSysRup.length;i++)
			srcIndexForFltSysRup[i] = -1;				// initialize values to -1 (no mapping due to zero rate or mag too small)
		int srcIndex = 0;
		// loop over FSS ruptures
		for(int r=0; r< rupSet.getNumRuptures();r++){
//			System.out.println("rate="+faultSysSolution.getRateForRup(r));
			if(faultSysSolution.getRateForRup(r) > 0.0 && isRuptureIncluded(r)) {
				numNonZeroFaultSystemSources +=1;
				fltSysRupIndexForSourceList.add(r);
				srcIndexForFltSysRup[r] = srcIndex;
				longTermRateOfFltSysRupInERF[r] = faultSysSolution.getRateForRup(r);
				srcIndex += 1;
			}
		}
		
		// convert the list to array
		if(fltSysRupIndexForSourceList.size() != numNonZeroFaultSystemSources)
			throw new RuntimeException("Problem");
		fltSysRupIndexForSource = new int[numNonZeroFaultSystemSources];
		for(int i=0;i<numNonZeroFaultSystemSources;i++)
			fltSysRupIndexForSource[i] = fltSysRupIndexForSourceList.get(i);
		
		if(D) {
			System.out.println("   " + numNonZeroFaultSystemSources+" of "+
					rupSet.getNumRuptures()+ 
					" fault system sources had non-zero rates");
		}
	}
	
	
	public double[] getLongTermRateOfFltSysRupInERF() {
		return longTermRateOfFltSysRupInERF;
	}
	
	
	/**
	 * This returns the fault system rupture index for the ith source
	 * @param iSrc
	 * @return
	 */
	public int getFltSysRupIndexForSource(int iSrc) {
		return fltSysRupIndexForSource[iSrc];
	}
	
	protected void readFaultSysSolutionFromFile() {
		// set input file
		File file = fileParam.getValue();
		if (file == null) throw new RuntimeException("No solution file specified");

		if (D) System.out.println("Loading solution from: "+file.getAbsolutePath());
		long runTime = System.currentTimeMillis();
		try {
			setSolution(FaultSystemSolution.load(file), false);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if(D) {
			runTime = (System.currentTimeMillis()-runTime)/1000;
			if(D) System.out.println("Loading solution took "+runTime+" seconds.");
		}
	}
	
	/**
	 * Set the current solution. Can overridden to ensure it is a particular subclass.
	 * This sets both faultSysSolutionChanged and bgRupTypeChanged as true.
	 * @param sol
	 */
	public void setSolution(FaultSystemSolution sol) {
		setSolution(sol, true);
	}
	
	private void setSolution(FaultSystemSolution sol, boolean clearFileParam) {
		this.faultSysSolution = sol;
		if (clearFileParam) {
			// this means that the method was called manually, clear the file param so that
			// any subsequent sets to the file parameter trigger an update and override this
			// current solution.
			synchronized (fileParam) {
				fileParam.removeParameterChangeListener(this);
				fileParam.setValue(null);
				fileParam.addParameterChangeListener(this);
			}
		}
		faultSysSolutionChanged = true;
		bgIncludeChanged = true;
		bgRupTypeChanged = true;  // because the background ruptures come from the FSS
		// have to set fileParamChanged to false in case you set the file param and then call
		// setSolution manually before doing an update forecast
		fileParamChanged = false;
	}
	
	public FaultSystemSolution getSolution() {
		return faultSysSolution;
	}

	@Override
	public int getNumSources() {
		if (bgInclude.equals(ONLY)) return numOtherSources;
		if (bgInclude.equals(EXCLUDE)) return numNonZeroFaultSystemSources;
		return numNonZeroFaultSystemSources + numOtherSources;
	}
	
	@Override
	public ProbEqkSource getSource(int iSource) {
		if (bgInclude.equals(ONLY)) {
			return getOtherSource(iSource);
		} else if(bgInclude.equals(EXCLUDE)) {
			return faultSourceList.get(iSource);
		} else if (iSource < numNonZeroFaultSystemSources) {
			return faultSourceList.get(iSource);
		} else {
			return getOtherSource(iSource - numNonZeroFaultSystemSources);
		}
	}
	
	protected boolean isGridSourceApplyAftershockFilter() {
		return false;
	}
	
	/**
	 * This returns a source that includes only the subseismo component
	 * for the grid cell.  This returns null is the iSource is fault based,
	 * or if the grid cell does not have any subseismo component.
	 * @param iSource
	 * @return
	 */
	public ProbEqkSource getSourceSubSeisOnly(int iSource) {
		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
		
		if (bgInclude.equals(ONLY)) {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceSubSeisOnFault(iSource, timeSpan.getDuration(),
						isGridSourceApplyAftershockFilter(), bgRupType);
		} else if(bgInclude.equals(EXCLUDE)) {
			return null;
		} else if (iSource < numNonZeroFaultSystemSources) {
			return null;
		} else {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceSubSeisOnFault(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
						isGridSourceApplyAftershockFilter(), bgRupType);
		}
	}
	
	
	/**
	 * This returns a source that includes only the truly off fault component
	 * for the grid cell.  This returns null is the iSource is fault based,
	 * or if the grid cell does not have any truly off fault component.
	 * @param iSource
	 * @return
	 */
	public ProbEqkSource getSourceTrulyOffOnly(int iSource) {
		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
		
		if (bgInclude.equals(ONLY)) {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceUnassociated(iSource, timeSpan.getDuration(),
						isGridSourceApplyAftershockFilter(), bgRupType);
		} else if(bgInclude.equals(EXCLUDE)) {
			return null;
		} else if (iSource < numNonZeroFaultSystemSources) {
			return null;
		} else {
			if (gridSources == null)
				return null;
			else
				return gridSources.getSourceUnassociated(iSource - numNonZeroFaultSystemSources, timeSpan.getDuration(),
						isGridSourceApplyAftershockFilter(), bgRupType);
		}
	}
	
	/**
	 * This makes all the fault-system sources and put them into faultSourceList
	 */
	private void makeAllFaultSystemSources() {
		faultSourceList = new ArrayList<>(numNonZeroFaultSystemSources);
		for (int i=0; i<numNonZeroFaultSystemSources; i++) {
			faultSourceList.add(makeFaultSystemSource(i));
		}
	}
	
	/**
	 * Returns a magnitude-frequency distribution for this rupture (e.g., for alternative magnitudes or aleatory
	 * variability), or null if only the mean magnitude should be used. Rates should be annualized and not include
	 * any time-dependence.
	 * 
	 * Default implementation checks for a rupture MFDs module
	 * 
	 * @param fltSystRupIndex
	 * @return
	 */
	protected DiscretizedFunc getFaultSysRupMFD(int fltSystRupIndex) {
		if (mfdsModule == null)
			return null;
		DiscretizedFunc rupMFD = mfdsModule.getRuptureMFD(fltSystRupIndex);	// this exists for multi-branch mean solutions
		if (rupMFD == null || rupMFD.size() < 2)
			return null;
		return rupMFD;
	}
	
	/**
	 * This returns the rate gain for the given fault system rupture index, e.g. due to time-dependence or aftershock
	 * corrections. Default implementation returns 1 (any time-dependence is handled by subclasses)
	 * 
	 * @param fltSystRupIndex
	 * @return rate gain
	 */
	protected double getFaultSysRupRateGain(int fltSystRupIndex) {
		return 1d;
	}
	
	/**
	 * @param fltSystRupIndex
	 * @return true if the fault system rupture is Poissonian, false otherwise. Default implementation returns true.
	 */
	protected boolean isFaultSysRupPoisson(int fltSystRupIndex) {
		return true;
	}
	
	protected String getFaultSysSourceName(int fltSystRupIndex) {
		// make and set the name
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		List<Integer> rupSects = rupSet.getSectionsIndicesForRup(fltSystRupIndex);
		FaultSection firstSect = rupSet.getFaultSectionData(rupSects.get(0));
		FaultSection lastSect = rupSet.getFaultSectionData(rupSects.get(rupSects.size()-1));
		return "Inversion Src #"+fltSystRupIndex+"; "+rupSects.size()+" SECTIONS BETWEEN "+firstSect.getName()+" AND "+lastSect.getName();
	}
	
	/**
	 * Creates a fault source.
	 * @param iSource - source index in ERF
	 * @return
	 */
	protected FaultRuptureSource makeFaultSystemSource(int iSource) {
		FaultSystemRupSet rupSet = faultSysSolution.getRupSet();
		int fltSystRupIndex = fltSysRupIndexForSource[iSource];
		
		double meanMag = rupSet.getMagForRup(fltSystRupIndex);	// this is the average if there are more than one mags
		
		DiscretizedFunc rupMFD = getFaultSysRupMFD(fltSystRupIndex);
		
		double duration = timeSpan.getDuration();
		
		double rateGain = getFaultSysRupRateGain(fltSystRupIndex);
		boolean isPoisson = isFaultSysRupPoisson(fltSystRupIndex);
		
		Preconditions.checkState(rateGain > 0, "Bad probGain=%s for rupIndex=%s", (Double)rateGain, fltSystRupIndex);
		
		FaultRuptureSource src;
		if (rupMFD == null || rupMFD.size() < 2) {
			// simple case, single rupture
			
			double annualRate = rateGain*longTermRateOfFltSysRupInERF[fltSystRupIndex];
			
			double prob;
			if (isPoisson)
				prob = 1d-Math.exp(-annualRate*duration);
			else
				// cannot exceed 1
				prob = Math.min(1d, annualRate*duration);
			
			src = new FaultRuptureSource(meanMag, 
					rupSet.getSurfaceForRupture(fltSystRupIndex, faultGridSpacing), 
					rupSet.getAveRakeForRup(fltSystRupIndex), prob, isPoisson);
		} else {
			// we have multiple magnitudes
			DiscretizedFunc rupMFDcorrected = rupMFD;
			if (rateGain != 1d) {
				rupMFDcorrected = rupMFD.deepClone();
				rupMFDcorrected.scale(rateGain);
			}
			
			src = new FaultRuptureSource(rupMFDcorrected, 
					rupSet.getSurfaceForRupture(fltSystRupIndex, faultGridSpacing),
					rupSet.getAveRakeForRup(fltSystRupIndex), timeSpan.getDuration(), isPoisson);
		}
		
		// set the name
		src.setName(getFaultSysSourceName(fltSystRupIndex));
		
		RupSetTectonicRegimes tectonics = rupSet.getModule(RupSetTectonicRegimes.class);
		if (tectonics != null) {
			// set the tectonic regime
			TectonicRegionType regime = tectonics.get(fltSystRupIndex);
			if (regime != null)
				src.setTectonicRegionType(regime);
		}
		return src;
	}
	
	/**
	 * This provides a mechanism for adding other sources in subclasses
	 * @param iSource - note that this index is relative to the other sources list (numFaultSystemSources has already been subtracted out)
	 * @return
	 */
	protected ProbEqkSource getOtherSource(int iSource) {
		Preconditions.checkNotNull(faultSysSolution, "Fault system solution is null");
		GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
		if (gridSources == null)
			return null;
		if (cacheGridSources) {
			synchronized (this) {
				if (gridSourceCache == null)
					gridSourceCache = new ProbEqkSource[numOtherSources];
				if (gridSourceCache[iSource] != null)
					return gridSourceCache[iSource];
			}
			// if we made it here, it's not cached
			gridSourceCache[iSource] = gridSources.getSource(iSource, timeSpan.getDuration(),
					isGridSourceApplyAftershockFilter(), bgRupType);
			return gridSourceCache[iSource];
		}
		return gridSources.getSource(iSource, timeSpan.getDuration(),
				isGridSourceApplyAftershockFilter(), bgRupType);
	}
	
	public void setCacheGridSources(boolean cacheGridSources) {
		this.cacheGridSources = cacheGridSources;
		if (!cacheGridSources)
			gridSourceCache = null;
	}
	
	/**
	 * Any subclasses that wants to include other (gridded) sources can override
	 * this method (and the getOtherSource() method), and make sure you return true if the
	 * number of ruptures changes.
	 */
	protected boolean initOtherSources() {
		if (bgIncludeChanged && bgInclude == EXCLUDE) {
			// we don't need to erase previously generated ones, but don't bother calling
			// getGridSourceProvider() below if we're not going to use them
			return false;
		}
		if(bgRupTypeChanged || bgIncludeChanged) {
			int prevOther = numOtherSources;
			GridSourceProvider gridSources = faultSysSolution.getGridSourceProvider();
			if (gridSources == null) {
				numOtherSources = 0;
				// return true only if we used to have grid sources but now don't
				return prevOther > 0;
			}
			numOtherSources = gridSources.size();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void timeSpanChange(EventObject event) {
		timeSpanChangeFlag = true;
	}
	
	/**
	 * This sets the following: totNumRups, nthRupIndicesForSource, srcIndexForNthRup[], 
	 * rupIndexForNthRup[], fltSysRupIndexForNthRup[], and totNumRupsFromFaultSystem.  
	 * The latter two are how this differs from the parent method.
	 * 
	 */
	@Override
	protected void setAllNthRupRelatedArrays() {
		
		if(D) System.out.println("Running setAllNthRupRelatedArrays()");
		
		totNumRups=0;
		totNumRupsFromFaultSystem=0;
		nthRupIndicesForSource = new ArrayList<int[]>();

		// make temp array lists to avoid making each source twice
		ArrayList<Integer> tempSrcIndexForNthRup = new ArrayList<Integer>();
		ArrayList<Integer> tempRupIndexForNthRup = new ArrayList<Integer>();
		ArrayList<Integer> tempFltSysRupIndexForNthRup = new ArrayList<Integer>();
		int n=0;
		
		for(int s=0; s<getNumSources(); s++) {	// this includes gridded sources
			int numRups = getSource(s).getNumRuptures();
			totNumRups += numRups;
			if(s<numNonZeroFaultSystemSources) {
				totNumRupsFromFaultSystem += numRups;
			}
			int[] nthRupsForSrc = new int[numRups];
			for(int r=0; r<numRups; r++) {
				tempSrcIndexForNthRup.add(s);
				tempRupIndexForNthRup.add(r);
				if(s<numNonZeroFaultSystemSources)
					tempFltSysRupIndexForNthRup.add(fltSysRupIndexForSource[s]);
				nthRupsForSrc[r]=n;
				n++;
			}
			nthRupIndicesForSource.add(nthRupsForSrc);
		}
		// now make final int[] arrays
		srcIndexForNthRup = new int[tempSrcIndexForNthRup.size()];
		rupIndexForNthRup = new int[tempRupIndexForNthRup.size()];
		fltSysRupIndexForNthRup = new int[tempFltSysRupIndexForNthRup.size()];
		for(n=0; n<totNumRups;n++)
		{
			srcIndexForNthRup[n]=tempSrcIndexForNthRup.get(n);
			rupIndexForNthRup[n]=tempRupIndexForNthRup.get(n);
			if(n<tempFltSysRupIndexForNthRup.size())
				fltSysRupIndexForNthRup[n] = tempFltSysRupIndexForNthRup.get(n);
		}
				
		if (D) {
			System.out.println("   getNumSources() = "+getNumSources());
			System.out.println("   totNumRupsFromFaultSystem = "+totNumRupsFromFaultSystem);
			System.out.println("   totNumRups = "+totNumRups);
		}
	}
	
	/**
	 * This returns the fault system rupture index for the Nth rupture
	 * @param nthRup
	 * @return
	 */
	public int getFltSysRupIndexForNthRup(int nthRup) {
		return fltSysRupIndexForNthRup[nthRup];
	}

	/**
	 * this returns the src index for a given fault-system rupture
	 * index
	 * @param fltSysRupIndex
	 * @return
	 */
	public int getSrcIndexForFltSysRup(int fltSysRupIndex) {
		return srcIndexForFltSysRup[fltSysRupIndex];
	}
	
	public int getTotNumRupsFromFaultSystem() {
		return totNumRupsFromFaultSystem;
	}
	
	public GridSourceProvider getGridSourceProvider() {
		return faultSysSolution.getGridSourceProvider();
	}
	
}
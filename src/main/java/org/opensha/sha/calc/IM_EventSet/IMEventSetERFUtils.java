package org.opensha.sha.calc.IM_EventSet;

import org.opensha.commons.util.ServerPrefUtils;
import org.opensha.commons.util.ServerPrefs;
import org.opensha.sha.earthquake.ERF_Ref;
import org.opensha.sha.gui.beans.ERF_GuiBean;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class IMEventSetERFUtils {

    // The following ERFs are excluded as they do not work for IM Event Set Calculations
    private static final List<ERF_Ref> EXCLUDED_ERFS = List.of(
            ERF_Ref.STEP_ALASKA,                // "STEP Alaskan Pipeline ERF"
            ERF_Ref.POISSON_FAULT,              // "Poisson Fault ERF"
            ERF_Ref.POISSON_FLOATING_FAULT,     // "Floating Poisson Fault ERF"
            ERF_Ref.PEER_MULTI_SOURCE,          // "PEER Multi Source"
            ERF_Ref.PEER_NON_PLANAR_FAULT,      // "PEER Non Planar Fault Forecast"
            ERF_Ref.PEER_AREA,                  // "PEER Area Forecast"
            ERF_Ref.PEER_LOGIC_TREE,            // "PEER Logic Tree ERF List"
            ERF_Ref.UCERF_2_TIME_INDEP_LIST,    // "UCERF2 ERF Epistemic List"
            ERF_Ref.UCERF3_EPISTEMIC,           // "UCERF3 Epistemic List ERF"
            ERF_Ref.WGCEP_02,                   // "WG02 Eqk Rup Forecast"
            ERF_Ref.WGCEP_02_LIST,              // "WG02 ERF List"
            ERF_Ref.POINT_SOURCE_MULTI_VERT,    // "Point 2 Mult Vertical SS Fault ERF"
            ERF_Ref.POINT_SOURCE_MULTI_VERT_LIST, // "Point2Mult Vertical SS Fault ERF List"
            ERF_Ref.YUCCA_MOUNTAIN_LIST,        // "Yucca Mountain ERF Epistemic List"
            ERF_Ref.YUCCA_MOUNTAIN,             // "Yucca mountain Adj. ERF"
            ERF_Ref.POINT_SOURCE                // "Point Source ERF"
    );

    public static List<ERF_Ref> getExcludedERFs() {
        return new ArrayList<>(EXCLUDED_ERFS);
    }

    public static Set<ERF_Ref> getSupportedERFs() {
        return getSupportedERFs(false, ServerPrefUtils.SERVER_PREFS);
    }

    public static Set<ERF_Ref> getSupportedERFs(boolean includeListERFs, ServerPrefs prefs) {
        Set<ERF_Ref> allERFs = ERF_Ref.get(includeListERFs, prefs);
        allERFs.removeAll(EXCLUDED_ERFS);
        return allERFs;
    }

    public static ERF_GuiBean createERF_GUI_Bean() {
        try {
            return new ERF_GuiBean(IMEventSetERFUtils.getSupportedERFs());
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}


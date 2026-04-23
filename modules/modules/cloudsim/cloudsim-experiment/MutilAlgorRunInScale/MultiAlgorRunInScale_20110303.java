package org.cloudbus.cloudsim.experiment.MutilAlgorRunInScale;

import org.cloudbus.cloudsim.experiment.*;
import org.cloudbus.cloudsim.power.DiscreteDEPSO;

import java.io.IOException;

public class MultiAlgorRunInScale_20110303 {
//    private static String[] dates ={"20110303","20110306","20110309","20110322","20110325",
//            "20110403","20110409","20110411","20110412","20110420"};
//        private static int[] cloudlets ={1052,1000,1060,1516,1078,1462,1358,1232,1054,1032};

//    private static String[] dates = {"vm-4000-1","vm-4000-2","vm-4000-3"};
//    private static String[] dates = {"vm-5000-1","vm-5000-2","vm-5000-3","vm-5000-4","vm-5000-5"};
//    private static String[] dates = {"20110322"};
    private static String date="20110303";
    private static int cloudlet=1052;
    public static void main(String[] args) throws IOException {
        Constants.workload= date;
        Constants.NUMBER_OF_CLOUDLETS=cloudlet;
        Constants.NUMBER_OF_VMS=cloudlet;
//        Ffd ffd = new Ffd();
//        ffd.main(null);
//        System.gc();
//
//        Mbfd mbfd = new Mbfd();
//        mbfd.main(null);
//        System.gc();
//
//        Peap peap = new Peap();
//        peap.main(null);
//        System.gc();
//
//        Gapso gapso = new Gapso();
//        gapso.main(null);
//        System.gc();
//        Pso pso = new Pso();
//        pso.main(null);
//        System.gc();
//
//        GRANITE granite = new GRANITE();
//        granite.main(null);
//        System.gc();
//
//        PTACO ptaco = new PTACO();
//        ptaco.main(null);
//        System.gc();

        DePso depso = new DePso();
        depso.main(null);
        System.gc();

//        ETAS etas = new ETAS();
//        etas.main(null);
//        System.gc();

        DiscreteDEPSO.fixedThreadPool.shutdown();
    }
}

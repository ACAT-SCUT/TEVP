package org.cloudbus.cloudsim.experiment.MutilAlgorRunInScale;

import org.cloudbus.cloudsim.experiment.*;
import org.cloudbus.cloudsim.power.DiscreteDEPSO;

import java.io.IOException;

public class MultiAlgorRunInScale_Azure_1200 {
    private static String date="vm-4000-1";
    private static int cloudlet=1200;

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
//        De de=new De();
//        de.main(null);
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

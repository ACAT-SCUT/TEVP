package org.cloudbus.cloudsim.cooling;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * computer room air conditioning(CRAC)
 * 假设机房中4个CRAC的冷却控制参数一致，计算出的是总冷却能耗；
 * 针对P1热感知VM整合方法所设定；
 * @ author: lin jianpeng
 * @ date: 2023/4/6
 */
public class CRACs {
    public final int CRAC_NUM=4;
    public final double MAX_TSUP=30.0;
    public final double MIN_TSUP=17.0;
    public final double MAX_FS=1.0;
    public final double MIN_FS=0.1;
    public double InitialTsup=22.0;    //初始供给温度值
    public double InitialFS=0.5;       //初始风扇转速值
    public double[] Tsups=new double[]{InitialTsup,InitialTsup,InitialTsup,InitialTsup};             // CRACs 供给温度设定值
    public double[] FSs=new double[]{InitialFS,InitialFS,InitialFS,InitialFS};               // CRACs 风扇转速设定值

    public double Cop;      //冷却性能
    public CRACs() {
        Cop=0.0;
    }

    public double getCoolingPower(double IT_power, double[] Tsups, double[] fanspeeds) {
        /**
         * @param IT_power  机房总IT功耗(W)
         * @param Tsup      供给温度
         * @param fanspeed  风扇转速比 [0,0.1,0.2,...,1.0]
         */
        // 空调功率
        double sum = 0.0;
        for (double sup: Tsups) {
            sum += sup;
        }
        double average_Tsup = sum / Tsups.length;
        Cop=0.0068*average_Tsup*average_Tsup+0.0008*average_Tsup+0.458;
        double power_air_comp=IT_power/Cop;
        // 风扇功率
        Map<Double,Double> power_fanspeed_map= new HashMap<>();
        power_fanspeed_map.put(0.0,0.0);
        power_fanspeed_map.put(0.1,50.0);
        power_fanspeed_map.put(0.2,60.0);
        power_fanspeed_map.put(0.3,75.0);
        power_fanspeed_map.put(0.4,90.0);
        power_fanspeed_map.put(0.5,110.0);
        power_fanspeed_map.put(0.6,140.0);
        power_fanspeed_map.put(0.7,170.0);
        power_fanspeed_map.put(0.8,210.0);
        power_fanspeed_map.put(0.9,250.0);
        power_fanspeed_map.put(1.0,300.0);

        double sum_fan_power=0.0;
        for (double fs: fanspeeds) {
            sum_fan_power+=power_fanspeed_map.get(fs);
        }
        double totalCoolingPower=power_air_comp+ sum_fan_power;
        return totalCoolingPower;

    }
    public double getCoolingPower(double IT_power) {
        /**
         * @param IT_power  机房总IT功耗(W)
         * @param Tsup      供给温度
         * @param fanspeed  风扇转速比 [0,0.1,0.2,...,1.0]
         */
        // 空调功率
        double sum = 0.0;
        for (double sup: this.Tsups) {
            sum += sup;
        }
        double average_Tsup = sum / this.Tsups.length;
        double CoP=0.0068*average_Tsup*average_Tsup+0.0008*average_Tsup+0.458;
        double power_air_comp=IT_power/CoP;

        // 风扇功率
        Map<Double,Double> power_fanspeed_map= new HashMap<>();
        power_fanspeed_map.put(0.0,0.0);
        power_fanspeed_map.put(0.1,100.0);
        power_fanspeed_map.put(0.2,200.0);
        power_fanspeed_map.put(0.3,400.0);
        power_fanspeed_map.put(0.4,650.0);
        power_fanspeed_map.put(0.5,1000.0);
        power_fanspeed_map.put(0.6,1300.0);
        power_fanspeed_map.put(0.7,1700.0);
        power_fanspeed_map.put(0.8,2200.0);
        power_fanspeed_map.put(0.9,2650.0);
        power_fanspeed_map.put(1.0,3000.0);

        double sum_fan_power=0.0;
        for (double fs: this.FSs) {
            sum_fan_power+=power_fanspeed_map.get(fs);
        }
        double totalCoolingPower=power_air_comp+ sum_fan_power;
        return totalCoolingPower;
    }

    public double getMAX_TSUP() {
        return MAX_TSUP;
    }

    public double getMIN_TSUP() {
        return MIN_TSUP;
    }

    public double getMAX_FS() {
        return MAX_FS;
    }

    public double getMIN_FS() {
        return MIN_FS;
    }

    public double[] getTsups() {
        return Tsups;
    }

    public void setTsups(double[] tsups) {
        Tsups = tsups;
    }

    public double[] getFSs() {
        return FSs;
    }

    public void setFSs(double[] FSs) {
        this.FSs = FSs;
    }

    public double getCop() {
        return Cop;
    }

    public void setCop(double cop) {
        Cop = cop;
    }
}

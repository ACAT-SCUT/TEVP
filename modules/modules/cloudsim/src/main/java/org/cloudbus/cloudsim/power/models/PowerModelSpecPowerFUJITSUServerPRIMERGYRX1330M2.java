package org.cloudbus.cloudsim.power.models;

public class PowerModelSpecPowerFUJITSUServerPRIMERGYRX1330M2 extends PowerModelSpecPower {
    /**
     * The power consumption and peff are according to the utilization percentage.
     * power的单位是（W），peff的单位是（ssj_ops）
     */
    private final double[] power = { 16.0, 20.1, 22.3, 24.1, 26.2, 28.4, 31.2, 35.2, 39.0, 43.6, 47.7};
    private final double[] peff = { 0, 2356, 4234, 5858, 7304, 8327, 9049, 9464, 9710, 9814, 9932};
    private final double PeakPower = 47.7;//峰值能耗
    private final double PeakPeff = 9932;//峰值效能比
    private final double pp_CPU_util = 1.0;//峰值效能比下的CPU利用率

    @Override
    protected double getPowerData(int index) {
        return power[index];
    }

    @Override
    protected double getPeffData(int index) {return peff[index];}

    @Override
    public final double[] getPowerList() {return power;}

    @Override
    public final double[] getPeffList() {return peff;}

    @Override
    public double getPp_CPU_tuil(){return pp_CPU_util;}

    @Override
    public double getPeakPower() {return PeakPower;}

    @Override
    public double getPeakPeff() {return PeakPeff;}
}

